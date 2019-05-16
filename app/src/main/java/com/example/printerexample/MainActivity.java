package com.example.printerexample;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

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

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    ReceiptPrinterService receiptPrinterService;
    boolean isBound = false;

    private TextView mDumpTextView;
    private ScrollView mScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDumpTextView = findViewById(R.id.consoleText);
        mScrollView = findViewById(R.id.demoScroller);

        Intent intent = new Intent(this,ReceiptPrinterService.class);
        bindService(intent,receiptPrinterConnection,Context.BIND_AUTO_CREATE);


        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    clearLog();
            }
        });

        Button printReceiptButton = findViewById(R.id.printReceiptButton);
        printReceiptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(receiptPrinterService == null){
                    return;
                }
                receiptPrinterService.printReceipt();
            }
        });


    }

    @Override
    protected void onStart() {
        super.onStart();
//        Intent intent = new Intent(this,ReceiptPrinterService.class);
//        bindService(intent,receiptPrinterConnection,Context.BIND_AUTO_CREATE);
        String b1 = Boolean.toString(isBound);
        Log.d(TAG, "onstart called, isBound is "+b1);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(receiptPrinterConnection);
        String b1 = Boolean.toString(isBound);
        Log.d(TAG, "onstop called, isBouond is "+b1);

    }

    private final ServiceConnection receiptPrinterConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            /*The onServiceConnected() method will be called when the client binds successfully to the service.
             The method is passed as an argument the IBinder object returned by the onBind() method of the service.
             This argument is cast to an object of type MyLocalBinder and then the getService() method of the
             binder object is called to obtain a reference to the service instance, which, in turn, is assigned to
             receiptPrinterService. A Boolean flag is used to indicate that the connection has been successfully established.*/
            ReceiptPrinterService.MyLocalBinder myLocalBinder = (ReceiptPrinterService.MyLocalBinder) service;
            receiptPrinterService = myLocalBinder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            /*The onServiceDisconnected() method is called when the connection ends and simply sets the Boolean flag to false.*/
            isBound = false;
        }
    };


    public void logReceivedMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDumpTextView.append("<< " + message + "\n\n");
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }
        });
    }

    private void clearLog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDumpTextView.setText("");
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }
        });
    }
}
