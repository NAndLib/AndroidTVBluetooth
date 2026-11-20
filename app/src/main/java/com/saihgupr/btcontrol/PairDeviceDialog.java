package com.saihgupr.btcontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Pair New Device" dialog: scans, lists named nearby devices as they appear, and pairs the
 * one the user selects. Callers must have checked permissions and Location first.
 */
@SuppressLint("MissingPermission")
final class PairDeviceDialog {

    interface Listener {
        void onPaired(BluetoothDevice device);
    }

    private static final long SCAN_MS = 40_000;
    private static final long BOND_MS = 30_000;

    private final AppCompatActivity activity;
    private final Listener listener;
    private final List<DiscoveredDevices.Entry> shown = new ArrayList<>();

    private DiscoveredDevices devices;
    private AlertDialog dialog;
    private ArrayAdapter<DiscoveredDevices.Entry> listAdapter;
    private ListView list;
    private TextView status;
    private ProgressBar progress;
    private Button rescanButton;
    private BluetoothDiscovery discovery;
    private BluetoothBonder bonder;

    PairDeviceDialog(AppCompatActivity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    void show() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_pair, null);
        list = view.findViewById(R.id.pair_list);
        status = view.findViewById(R.id.pair_status);
        progress = view.findViewById(R.id.pair_progress);

        listAdapter = new ArrayAdapter<DiscoveredDevices.Entry>(activity, R.layout.item_discovered_device, shown) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView label = (TextView) super.getView(position, convertView, parent);
                label.setText(styledLabel(getItem(position)));
                return label;
            }
        };
        list.setAdapter(listAdapter);
        list.setOnItemClickListener((parent, v, position, id) -> pair(shown.get(position)));

        dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_AndroidTVBluetooth_Dialog)
                .setTitle(R.string.pair_title)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.pair_rescan, null)
                .create();
        dialog.setOnDismissListener(d -> release());
        dialog.setOnShowListener(d -> {
            // Override the neutral button so "Scan Again" does not close the dialog.
            rescanButton = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            rescanButton.setOnClickListener(v -> startScan());
            startScan();
        });
        dialog.show();
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    void dismiss() {
        if (dialog != null) dialog.dismiss();
    }

    private void startScan() {
        if (bonder != null) return;
        if (discovery != null) discovery.stop();

        devices = new DiscoveredDevices(bondedAddresses());
        shown.clear();
        listAdapter.notifyDataSetChanged();

        discovery = new BluetoothDiscovery(activity, SCAN_MS, new BluetoothDiscovery.Listener() {
            @Override
            public void onFound(BluetoothDevice device, String name) {
                if (devices.add(device.getAddress(), name, device.getType())) {
                    shown.clear();
                    shown.addAll(devices.visible());
                    listAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFinished() {
                showIdle();
            }
        });
        if (discovery.start()) {
            setBusy(true);
            setStatus(activity.getString(R.string.pair_scanning), false);
        } else {
            setBusy(false);
            setStatus(activity.getString(R.string.pair_scan_failed), true);
        }
    }

    private void pair(DiscoveredDevices.Entry entry) {
        if (bonder != null) return;
        if (discovery != null) discovery.stop();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;
        final BluetoothDevice device = adapter.getRemoteDevice(entry.address);
        final String name = entry.name;

        list.setEnabled(false);
        setBusy(true);
        setStatus(activity.getString(R.string.pair_pairing, name), false);

        bonder = new BluetoothBonder(activity, device, BOND_MS, (ok, message) -> {
            bonder = null;
            if (ok) {
                Toast.makeText(activity, activity.getString(R.string.pair_success, name), Toast.LENGTH_SHORT).show();
                listener.onPaired(device);
                dismiss();
            } else {
                list.setEnabled(true);
                setBusy(false);
                setStatus(message, true);
            }
        });
        bonder.start();
    }

    private void showIdle() {
        setBusy(false);
        if (!shown.isEmpty()) {
            setStatus(activity.getString(R.string.pair_scan_done), false);
        } else if (devices.hiddenCount() > 0) {
            setStatus(activity.getString(R.string.pair_scan_empty_unnamed, devices.hiddenCount()), false);
        } else {
            setStatus(activity.getString(R.string.pair_scan_empty), false);
        }
    }

    /** Busy = scanning or pairing: spinner on, "Scan Again" off. */
    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (rescanButton != null) rescanButton.setEnabled(!busy);
    }

    private void setStatus(String text, boolean error) {
        status.setText(text);
        status.setTextColor(ContextCompat.getColor(activity, error ? R.color.status_error : R.color.text_muted));
    }

    private Set<String> bondedAddresses() {
        Set<String> addresses = new HashSet<>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> bonded = adapter != null ? adapter.getBondedDevices() : null;
        if (bonded != null) {
            for (BluetoothDevice device : bonded) {
                addresses.add(device.getAddress());
            }
        }
        return addresses;
    }

    /** Name on the first line, address in smaller muted text below. */
    private CharSequence styledLabel(DiscoveredDevices.Entry entry) {
        String label = entry.label();
        int split = label.indexOf('\n');
        SpannableStringBuilder text = new SpannableStringBuilder(label);
        if (split >= 0) {
            text.setSpan(new RelativeSizeSpan(0.8f), split, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(activity, R.color.text_muted)),
                    split, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private void release() {
        if (discovery != null) discovery.stop();
        if (bonder != null) {
            bonder.cancel();
            bonder = null;
        }
    }
}
