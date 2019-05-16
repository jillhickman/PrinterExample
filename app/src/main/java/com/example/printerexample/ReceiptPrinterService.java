package com.example.printerexample;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import co.poynt.os.model.AccessoryProvider;
import co.poynt.os.model.AccessoryProviderFilter;
import co.poynt.os.model.AccessoryType;
import co.poynt.os.model.PoyntError;
import co.poynt.os.model.PrintedReceiptV2;
import co.poynt.os.model.PrinterStatus;
import co.poynt.os.services.v1.IPoyntAccessoryManagerListener;
import co.poynt.os.services.v1.IPoyntPrinterService;
import co.poynt.os.services.v1.IPoyntPrinterServiceListener;
import co.poynt.os.util.AccessoryProviderServiceHelper;

public class ReceiptPrinterService extends Service {
    //a new instance of MyLocalBinder is created and assigned
    private final IBinder myBinder = new MyLocalBinder();
    private static final String TAG = MainActivity.class.getSimpleName();

    private List<AccessoryProvider> providers;
    private AccessoryProviderServiceHelper accessoryProviderServiceHelper;
    private HashMap<AccessoryProvider, IBinder> printerServices = new HashMap<>();


    @Override
    public IBinder onBind(Intent intent) {
        connectToAccessoryProviderServiceHelperAndMakeCallback();
        return myBinder;
    }

    private void connectToAccessoryProviderServiceHelperAndMakeCallback() {
        try {

            AccessoryProviderServiceHelper.AccessoryManagerConnectionCallback AMCCallback = makeAMCCallback();

            // initialize capabilityProviderServiceHelper
            accessoryProviderServiceHelper = new AccessoryProviderServiceHelper(this);

            // connect to accessory manager service
            accessoryProviderServiceHelper.bindAccessoryManager(AMCCallback);

        } catch (SecurityException e) {
            Log.e(TAG, "Failed to connect to capability or accessory manager", e);
        }
    }
    // we do this if there are multiple accessories connected of the same type/provider
    private List<AccessoryProvider> findMatchingProviders(AccessoryProvider provider) {
        ArrayList<AccessoryProvider> matchedProviders = new ArrayList<>();
        if (providers != null) {
            for (AccessoryProvider printer : providers) {
                if (provider.getAccessoryType() == printer.getAccessoryType()
                        && provider.getPackageName().equals(printer.getPackageName())
                        && provider.getClassName().equals(printer.getClassName())) {
                    matchedProviders.add(printer);
                }
            }
        }
        return matchedProviders;
    }
    private AccessoryProviderServiceHelper.AccessoryManagerConnectionCallback makeAMCCallback() {
        return new AccessoryProviderServiceHelper.AccessoryManagerConnectionCallback() {
            @Override
            public void onConnected(AccessoryProviderServiceHelper accessoryProviderServiceHelper) {
                // when connected, check if we have any printers registered
                if (accessoryProviderServiceHelper.getAccessoryServiceManager() != null) {
                    AccessoryProviderFilter filter = new AccessoryProviderFilter(AccessoryType.PRINTER);
                    Log.d(TAG, "trying to get Printer accessory...");
                    try {
                        // look up the printers using the filter
                        accessoryProviderServiceHelper.getAccessoryServiceManager().getAccessoryProviders(
                                filter, poyntAccessoryManagerListener);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Unable to connect to Accessory Service", e);
                    }
                } else {
                    Log.d(TAG, "Not connected with accessory service manager");
                }
            }

            @Override
            public void onDisconnected(AccessoryProviderServiceHelper accessoryProviderServiceHelper) {
                Log.d(TAG, "Disconnected with accessory service manager");
            }
        };
    }

    // this is the accessory manager listener which gets invoked when accessory manager completes
    // scanning for the requested accessories
    private IPoyntAccessoryManagerListener poyntAccessoryManagerListener
            = new IPoyntAccessoryManagerListener.Stub() {

        @Override
        public void onError(PoyntError poyntError) throws RemoteException {
            Log.e(TAG, "Failed to connect to accessory manager: " + poyntError);
        }

        @Override
        public void onSuccess(final List<AccessoryProvider> printers) throws RemoteException {
            // now that we are connected - request service connections to each accessory provider
            if (printers != null && printers.size() > 0) {
                // save it for future reference
                providers = printers;
                if (accessoryProviderServiceHelper.getAccessoryServiceManager() != null) {
                    // for each printer accessory - request "service" connections if it's still connected
                    for (AccessoryProvider printer : printers) {
                        Log.d(TAG, "Printer: " + printer.toString());
                        if (printer.isConnected()) {
                            // request service connection binder
                            // IMP: note that this method returns service connection if it already exists
                            // hence the need for both connection callback and the returned value
                            IBinder binder = accessoryProviderServiceHelper.getAccessoryService(
                                    printer, AccessoryType.PRINTER,
                                    providerConnectionCallback);
                            //already cached connection.
                            if (binder != null) {
                                printerServices.put(printer, binder);
                            }
                        }
                    }
                }
            } else {
                Log.d(TAG, "No Printer found");
            }
        }
    };

    // this is the callback for the service connection to each accessory provider service in this case
    // the android service supporting printer accessory
    private AccessoryProviderServiceHelper.ProviderConnectionCallback providerConnectionCallback = new AccessoryProviderServiceHelper.ProviderConnectionCallback() {

        @Override
        public void onConnected(AccessoryProvider provider, IBinder binder) {
            // in some cases multiple accessories of the same type (eg. two cash drawers of same
            // make/model or two star printers) might be supported by the same android service
            // so here we check if we need to share the same service connection for more than
            // one accessory provider
            List<AccessoryProvider> otherProviders = findMatchingProviders(provider);
            // all of them share the same service binder
            for (AccessoryProvider matchedProvider : otherProviders) {
                printerServices.put(matchedProvider, binder);
            }
        }

        @Override
        public void onDisconnected(AccessoryProvider provider, IBinder binder) {
            // set the lookup done to false so we can try looking up again if needed
            if (printerServices != null && printerServices.size() > 0) {
                printerServices.remove(binder);
                // try to renew the connection.
                if (accessoryProviderServiceHelper.getAccessoryServiceManager() != null) {
                    IBinder binder2 = accessoryProviderServiceHelper.getAccessoryService(
                            provider, AccessoryType.PRINTER,
                            providerConnectionCallback);
                    if (binder2 != null) {//already cached connection.
                        printerServices.put(provider, binder2);
                    }
                }
            }
        }
    };
    private IPoyntPrinterServiceListener iPoyntPrinterServiceListener = new IPoyntPrinterServiceListener.Stub() {
        @Override
        public void onPrintResponse(PrinterStatus printerStatus, String s) throws RemoteException {
            if (printerStatus != null && printerStatus.getCode() == PrinterStatus.Code.PRINTER_DISCONNECTED ||
                    printerStatus.getCode() == PrinterStatus.Code.PRINTER_ERROR_OTHER ||
                    printerStatus.getCode() == PrinterStatus.Code.PRINTER_ERROR_OUT_OF_PAPER ||
                    printerStatus.getCode() == PrinterStatus.Code.PRINTER_ERROR_IMAGE_OFFDOCK) {
                Log.d(TAG,"Failed to print receipt");
            }
        }
    };
    public void printReceipt() {
        if (providers != null && providers.size() > 0) {
            Log.d(TAG,"Printing receipt...");

            for (AccessoryProvider provider : providers) {
                try {
                    IBinder binder = printerServices.get(provider);
                    if (binder != null) {
                        IPoyntPrinterService receiptPrinterService = IPoyntPrinterService.Stub.asInterface(binder);
                        if (receiptPrinterService == null) {
                            Log.e("crap", "print service is null");
                            return;
                        }
                        Log.d(TAG, "Printing receipt");
                        receiptPrinterService.printReceipt(
                                provider.getProviderName(),
                                new PrintedReceiptV2(),
                                iPoyntPrinterServiceListener,
                                new Bundle()
                        );

                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to communicate with printer", e);
                    Log.d(TAG,"No connected printers found");
                }
            }
        } else {
            Log.e(TAG, "Printer not connected");
            Log.d(TAG,"No connected printers found");
        }
    }

    /* This class contains a single method for the sole purpose of returning a
    reference to the current instance of the ReceiptPrinterService class*/
    public class MyLocalBinder extends Binder{

        ReceiptPrinterService getService(){
            return ReceiptPrinterService.this;
        }
    }
}
