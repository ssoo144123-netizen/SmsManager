package com.example.smsmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Telephony;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    static class Sms {
        long id, date;
        String address, body;
        boolean read, selected;
    }

    private static final int PERM_REQ = 1;
        private static final String[] PERMS = {
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private SharedPreferences prefs;
    private SmsAdapter adapter;
    private Spinner spinner;
    private TextView info;

    @Override
    protected void onCreate(Bundle state) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        setTheme(prefs.getBoolean("dark", false) ? R.style.DarkTheme : R.style.LightTheme);
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        spinner = findViewById(R.id.spinnerFilter);
        info = findViewById(R.id.txtInfo);

        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"همه", "خوانده‌نشده", "خوانده‌شده"}));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { load(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        adapter = new SmsAdapter();
        ((ListView) findViewById(R.id.listView)).setAdapter(adapter);

        requestPerms();
    }

    private void requestPerms() {
        boolean ok = true;
        for (String p : PERMS) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) ok = false;
        }
        if (ok) load();
        else requestPermissions(PERMS, PERM_REQ);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        load();
    }

    private boolean hasReadPerm() {
        return checkSelfPermission(android.Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isDefaultSms() {
        return getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }

    private void askDefaultSms() {
        Toast.makeText(this, "برای این کار باید پیامک‌رسان پیش‌فرض باشید", Toast.LENGTH_LONG).show();
        Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        startActivity(i);
    }

    private void load() {
        if (!hasReadPerm() || adapter == null) return;

        int pos = spinner.getSelectedItemPosition();
        String selection = pos == 1 ? Telephony.Sms.READ + "=0"
                : pos == 2 ? Telephony.Sms.READ + "=1" : null;

        adapter.clear();
        try (Cursor c = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms._ID, Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ},
                selection, null, Telephony.Sms.DATE + " DESC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    Sms s = new Sms();
                    s.id = c.getLong(0);
                    s.address = c.getString(1);
                    s.body = c.getString(2);
                    s.date = c.getLong(3);
                    s.read = c.getInt(4) == 1;
                    adapter.add(s);
                }
            }
        }
        updateInfo();
    }

    private List<Sms> selected() {
        List<Sms> out = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).selected) out.add(adapter.getItem(i));
        }
        return out;
    }

    private void updateInfo() {
        info.setText("تعداد: " + adapter.getCount() + "  |  انتخاب‌شده: " + selected().size());
    }

    public void onSelectAll(View v) {
        boolean allSelected = selected().size() == adapter.getCount();
        for (int i = 0; i < adapter.getCount(); i++) adapter.getItem(i).selected = !allSelected;
        adapter.notifyDataSetChanged();
        updateInfo();
    }

    public void onDelete(View v) {
        final List<Sms> sel = selected();
        if (sel.isEmpty()) { toast("پیامکی انتخاب نشده"); return; }
        if (!isDefaultSms()) { askDefaultSms(); return; }

        new AlertDialog.Builder(this)
                .setMessage("حذف " + sel.size() + " پیامک؟ این کار قابل بازگشت نیست.")
                .setPositiveButton("حذف", (d, w) -> {
                    for (Sms s : sel) {
                        getContentResolver().delete(Telephony.Sms.CONTENT_URI,
                                "_id=?", new String[]{String.valueOf(s.id)});
                    }
                    toast(sel.size() + " پیامک حذف شد");
                    load();
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    public void onMarkRead(View v) { setRead(true); }
    public void onMarkUnread(View v) { setRead(false); }

    private void setRead(boolean read) {
        List<Sms> sel = selected();
        if (sel.isEmpty()) { toast("پیامکی انتخاب نشده"); return; }
        if (!isDefaultSms()) { askDefaultSms(); return; }

        ContentValues values = new ContentValues();
        values.put(Telephony.Sms.READ, read ? 1 : 0);
        for (Sms s : sel) {
            getContentResolver().update(Telephony.Sms.CONTENT_URI, values,
                    "_id=?", new String[]{String.valueOf(s.id)});
        }
        toast("وضعیت " + sel.size() + " پیامک تغییر کرد");
        load();
    }

    public void onSave(View v) {
        List<Sms> sel = selected();
        if (sel.isEmpty()) { toast("پیامکی انتخاب نشده"); return; }

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        dir.mkdirs();
        String name = "sms_backup_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        File file = new File(dir, name);

        SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            for (Sms s : sel) {
                w.write("[" + df.format(new Date(s.date)) + "] " + s.address
                        + (s.read ? "" : " (نخوانده)") + "\n"
                        + s.body + "\n\n----------\n\n");
            }
            toast("ذخیره شد: " + file.getAbsolutePath());
        } catch (IOException e) {
            toast("خطا در ذخیره: " + e.getMessage());
        }
    }

    public void onThemeToggle(View v) {
        boolean dark = prefs.getBoolean("dark", false);
        prefs.edit().putBoolean("dark", !dark).apply();
        recreate();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class SmsAdapter extends ArrayAdapter<Sms> {
        private final SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US);

        SmsAdapter() { super(MainActivity.this, 0); }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            View v = LayoutInflater.from(getContext()).inflate(R.layout.item_sms, parent, false);
            final Sms s = getItem(pos);

            CheckBox cb = v.findViewById(R.id.cb);
            TextView t = v.findViewById(R.id.txt);

            cb.setOnCheckedChangeListener(null);
            cb.setChecked(s.selected);
            cb.setOnCheckedChangeListener((b, checked) -> {
                s.selected = checked;
                updateInfo();
            });
            v.setOnClickListener(x -> cb.toggle());

            t.setText(s.address + "  •  " + df.format(new Date(s.date))
                    + (s.read ? "" : "  (جدید)") + "\n" + s.body);
            return v;
        }
    }
}
