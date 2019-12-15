package li.doerf.hacked.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import li.doerf.hacked.CustomEvent;
import li.doerf.hacked.HackedApplication;
import li.doerf.hacked.R;
import li.doerf.hacked.remote.pwnedpasswords.PwnedPassword;
import li.doerf.hacked.ui.HibpInfo;
import li.doerf.hacked.utils.StringHelper;

/**
 * Use the {@link PasswordFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PasswordFragment extends Fragment {
    private static final String TAG = "PasswordFragment";
    private EditText passwordEditText;
    private ProgressBar progressBar;
    private TextView passwordOk;
    private TextView passwordPwned;
    private LocalBroadcastReceiver myBroadcastReceiver;

    public PasswordFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PasswordFragment.
     */
    public static PasswordFragment newInstance() {
        PasswordFragment fragment = new PasswordFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_password, container, false);

        passwordEditText = view.findViewById(R.id.password);
        
        Button button = view.findViewById(R.id.check_pwned);
        button.setOnClickListener(view1 -> checkPassword());
        
        progressBar = view.findViewById(R.id.progressbar);
        passwordOk = view.findViewById(R.id.result_ok);
        passwordPwned = view.findViewById(R.id.result_pwned);

        HibpInfo.prepare( getContext(), view.findViewById(R.id.hibp_info), null);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver();
        ((HackedApplication) getActivity().getApplication()).trackView("Fragment~Password");
    }

    @Override
    public void onPause() {
        unregisterReceiver();
        super.onPause();
    }

    private void checkPassword() {
        String password = passwordEditText.getText().toString();
        passwordOk.setVisibility(View.GONE);
        passwordPwned.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        new PwnedPassword(LocalBroadcastManager.getInstance(getContext())).check(password);
        ((HackedApplication) getActivity().getApplication()).trackCustomEvent(CustomEvent.CHECK_PASSWORD_PWNED);
    }

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter(PwnedPassword.BROADCAST_ACTION_PASSWORD_PWNED);
        myBroadcastReceiver = new LocalBroadcastReceiver();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(myBroadcastReceiver, intentFilter);
    }

    private void unregisterReceiver() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(myBroadcastReceiver);
        myBroadcastReceiver = null;
    }

    private void handleResult(boolean pwned, int numPwned, boolean exception) {
        progressBar.setVisibility(View.GONE);

        if ( exception ) {
            ((HackedApplication) getActivity().getApplication()).trackCustomEvent(CustomEvent.PASSWORD_PWNED_EXCEPTION);
            Toast.makeText(getContext(), getString(R.string.error_download_data), Toast.LENGTH_SHORT).show();
        } else if ( ! pwned ) {
            ((HackedApplication) getActivity().getApplication()).trackCustomEvent(CustomEvent.PASSWORD_NOT_PWNED);
            passwordOk.setVisibility(View.VISIBLE);
        } else {
            ((HackedApplication) getActivity().getApplication()).trackCustomEvent(CustomEvent.PASSWORD_PWNED);
            passwordPwned.setVisibility(View.VISIBLE);
            String t = getString(R.string.password_pwned, StringHelper.addDigitSeperator(Integer.toString(numPwned)));
            passwordPwned.setText(t);
        }
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "received local broadcast message");
            handleResult(
                    intent.getBooleanExtra(PwnedPassword.EXTRA_PASSWORD_PWNED, false),
                    intent.getIntExtra(PwnedPassword.EXTRA_PASSWORD_PWNED_Count, 0),
                    intent.getBooleanExtra(PwnedPassword.EXTRA_EXCEPTION, false));
        }

    }
}
