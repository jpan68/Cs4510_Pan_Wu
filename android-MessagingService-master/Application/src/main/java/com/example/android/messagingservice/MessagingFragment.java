/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.messagingservice;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.util.Map;
import java.util.HashMap;

/**
 * The main fragment that shows the buttons and the text view containing the log.
 */
public class MessagingFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = MessagingFragment.class.getSimpleName();

    // [START declare_database_ref]
    private DatabaseReference mDatabase;

    private Button mSendSingleConversation;
    private Button mSendTwoConversations;
    private Button mSendConversationWithFiveMessages;
    private Button mGetAllMessages;
    private TextView mDataPortView;
    private Button mClearLogButton;
    

    private Messenger mService;
    private boolean mBound;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mService = new Messenger(service);
            mBound = true;
            setButtonsState(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            mBound = false;
            setButtonsState(false);
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (MessageLogger.LOG_KEY.equals(key)) {
                mDataPortView.setText(MessageLogger.getAllMessages(getActivity()));
            }
        }
    };

    public MessagingFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_message_me, container, false);

        mSendSingleConversation = (Button) rootView.findViewById(R.id.send_1_conversation);
        mSendSingleConversation.setOnClickListener(this);

        mSendTwoConversations = (Button) rootView.findViewById(R.id.send_2_conversations);
        mSendTwoConversations.setOnClickListener(this);

        mSendConversationWithFiveMessages =
                (Button) rootView.findViewById(R.id.send_1_conversation_5_messages);
        mSendConversationWithFiveMessages.setOnClickListener(this);
        
        mGetAllMessages = (Button) rootView.findViewById(R.id.get_all_messages_sent);
        mGetAllMessages.setOnClickListener(this);

        mDataPortView = (TextView) rootView.findViewById(R.id.data_port);
        mDataPortView.setMovementMethod(new ScrollingMovementMethod());

        mClearLogButton = (Button) rootView.findViewById(R.id.clear);
        mClearLogButton.setOnClickListener(this);

        setButtonsState(false);

        // [START initialize_database_ref]
        mDatabase = FirebaseDatabase.getInstance().getReference();

        return rootView;
    }

    @Override
    public void onClick(View view) {
        if (view == mSendSingleConversation) {
            sendMsg(1, 1);
//            writeNewNumMsgsSent(1);
        } else if (view == mSendTwoConversations) {
            sendMsg(2, 1);
//            writeNewNumMsgsSent(1);
        } else if (view == mSendConversationWithFiveMessages) {
            sendMsg(1, 5);
//            writeNewNumMsgsSent(5);
        } else if (view==mGetAllMessages)
        {
            DatabaseReference mMessage = mDatabase.child("messages");
            ValueEventListener valueEventListener = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    String Text = "";
                    for (DataSnapshot msg : dataSnapshot.getChildren()) {
                        Text += "ID" + "  " + msg.getKey() + "\n";

                        List<String> subMsgs = (List<String>) msg.getValue();
                        for(int i = 0 ; i < subMsgs.size() ; i++){
                            Text += "---" + i + ":" + subMsgs.get(i) + "\n";
                        }
                    }
                    mDataPortView.setText(Text);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            mMessage.addListenerForSingleValueEvent(valueEventListener);
        }
       else if (view == mClearLogButton) {
            MessageLogger.clear(getActivity());
            mDataPortView.setText(MessageLogger.getAllMessages(getActivity()));
        }
    }

    private void writeNewNumMsgsSent(int numMsgs) {
        String key = mDatabase.child("num-msg-sent").push().getKey();
        Map<String, Object> childUpdate = new HashMap<>();
        childUpdate.put(key, numMsgs);
        mDatabase.updateChildren(childUpdate);
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().bindService(new Intent(getActivity(), MessagingService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        super.onPause();
        MessageLogger.getPrefs(getActivity()).unregisterOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDataPortView.setText(MessageLogger.getAllMessages(getActivity()));
        MessageLogger.getPrefs(getActivity()).registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mBound) {
            getActivity().unbindService(mConnection);
            mBound = false;
        }
    }

    private void sendMsg(int howManyConversations, int messagesPerConversation) {
        if (mBound) {
            Message msg = Message.obtain(null, MessagingService.MSG_SEND_NOTIFICATION,
                    howManyConversations, messagesPerConversation);
            try {
                mService.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Error sending a message", e);
                MessageLogger.logMessage(getActivity(), "Error occurred while sending a message.");
            }
        }
    }

    private void setButtonsState(boolean enable) {
        mSendSingleConversation.setEnabled(enable);
        mSendTwoConversations.setEnabled(enable);
        mSendConversationWithFiveMessages.setEnabled(enable);
    }
}
