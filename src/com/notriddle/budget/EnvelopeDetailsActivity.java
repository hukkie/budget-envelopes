/*
 * This file is a part of Budget with Envelopes.
 * Copyright 2013 Michael Howell <michael@notriddle.com>
 *
 * Budget is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Budget is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Budget. If not, see <http://www.gnu.org/licenses/>.
 */

package com.notriddle.budget;

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.MeasureSpec;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.database.sqlite.SQLiteDatabase;

public class EnvelopeDetailsActivity extends LockedActivity
                                     implements LoaderCallbacks<Cursor>,
                                                TextWatcher,
                                                DeleteAdapter.Deleter,
                                               AdapterView.OnItemClickListener {
    EditTextDefaultFocus mName;
    int mId;
    SQLiteDatabase mDatabase;
    LogAdapter mLogAdapter;
    DeleteAdapter mDeleteAdapter;
    ListView mLogView;
    SimpleEnvelopesAdapter mNavAdapter;
    ListView mNavView;
    TextView mAmount;
    TextView mAmountName;
    TextView mProjected;
    int mColor;
    boolean mLoadedEnvelope;
    boolean mLoadedLog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.envelopedetailsactivity);
        mId = state != null && state.containsKey("com.notriddle.budget.envelope")
              ? state.getInt("com.notriddle.budget.envelope")
              : getIntent().getIntExtra("com.notriddle.budget.envelope", -1);
        mName = new EditTextDefaultFocus(this);
        mName.setHint(getText(R.string.envelopeName_hint));
        mName.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mName.setLayoutParams(new ActionBar.LayoutParams(
            ActionBar.LayoutParams.FILL_PARENT,
            ActionBar.LayoutParams.WRAP_CONTENT
        ));
        mName.setSingleLine(true);
        mLoadedEnvelope = false;
        mLoadedLog = false;

        final ListView nV = mNavView = (ListView) findViewById(R.id.nav);
        if (nV != null) {
            mNavAdapter = new SimpleEnvelopesAdapter(this, null, R.layout.card_just_text);
            mNavAdapter.setExpanded(true);
            nV.setAdapter(mNavAdapter);
            nV.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            nV.setOnItemClickListener(this);
        }

        getLoaderManager().initLoader(0, null, this);
        getLoaderManager().initLoader(1, null, this);

        ActionBar ab = getActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setDisplayShowCustomEnabled(true);
        ab.setCustomView(mName);
        ab.setDisplayHomeAsUpEnabled(true);
        mName.addTextChangedListener(this);
        mLogAdapter = new LogAdapter(this, null);
        final ListView lV = mLogView = (ListView) findViewById(android.R.id.list);
        //lV.setOverScrollMode(lV.OVER_SCROLL_NEVER);
        final View head = getLayoutInflater().inflate(
            R.layout.totalamount,
            lV,
            false
        );
        head.setBackgroundResource(R.color.windowBackground);
        final int basePadding = head.getPaddingTop();
        mAmount = (TextView) head.findViewById(R.id.value);
        mAmountName = (TextView) head.findViewById(R.id.name);
        mProjected = (TextView) head.findViewById(R.id.projectedValue);
        lV.addHeaderView(head);
        mDeleteAdapter = new DeleteAdapter(
            this, this, mLogAdapter, R.color.windowBackground
        );
        lV.setAdapter(mDeleteAdapter);

        lV.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScroll(AbsListView lV, int first, int count, int total) {
                View c = lV.getChildAt(0);
                if (c == head) {
                    int move = (head.getTop()*-2)/3;
                    mAmount.setTranslationY(move);
                    mAmountName.setTranslationY(move);
                    mProjected.setTranslationY(move);
                }
            }
            public void onScrollStateChanged(AbsListView lV, int state) {
                // Do nothing.
            }
        });
        lV.setBackgroundResource(R.color.cardBackground);
        lV.setOnItemClickListener(this);
        getWindow().setBackgroundDrawable(null);
        lV.setSelector(android.R.color.transparent);
        lV.setDivider(getResources().getDrawable(R.color.cardDivider));
        lV.setDividerHeight((int)TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()
        ));
        lV.setOverscrollFooter(getResources().getDrawable(
            R.color.cardBackground
        ));
        lV.setOverscrollHeader(getResources().getDrawable(
            R.color.windowBackground
        ));
    }

    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("com.notriddle.budget.envelope", mId);
    }

    @Override public void onPause() {
        super.onPause();
        mDeleteAdapter.performDelete();
        if (mDatabase != null) {
            mDatabase.close();
            mDatabase = null;
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (mName.getText().length() == 0 && mLogAdapter.getCount() == 0 && mLoadedEnvelope && mLoadedLog) {
            deleteThis();
            mDatabase.close();
            mDatabase = null;
        }
    }

    @Override public void performDelete(long id) {
        deleteTransaction((int)id);
    }
    @Override public void onDelete(long id) {
        getLoaderManager().restartLoader(
            1, null, this
        );
    }
    @Override public void undoDelete(long id) {
        getLoaderManager().restartLoader(
            0, null, this
        );
    }

    private void deleteTransaction(int id) {
        needDatabase();
        mDatabase.beginTransaction();
        try {
            mDatabase.execSQL("DELETE FROM log WHERE _id = ?",
                              new String[] {Integer.toString(id)});
            EnvelopesOpenHelper.playLog(mDatabase);
            mDatabase.setTransactionSuccessful();
            getContentResolver().notifyChange(EnvelopesOpenHelper.URI, null);
        } finally {
            mDatabase.endTransaction();
        }
    }

    private SQLiteDatabase needDatabase() {
        if (mDatabase == null) {
            mDatabase = (new EnvelopesOpenHelper(this)).getWritableDatabase();
        }
        return mDatabase;
    }

    @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String table     = id == 0
                           ? "envelopes"
                           : "log";
        String[] columns = id == 0
                           ? new String[] { "name", "cents", "projectedCents",
                                            "color", "_id" }
                           : new String[] { "description", "cents", "time",
                                            "_id" };
        String where     = id == 0
                           ? (mNavAdapter == null ? "_id = ?" : null)
                           : "envelope = ?";
        String[] wArgs   = id == 0 && mNavAdapter != null
                           ? null
                           : new String[] {Integer.toString(mId)};
        String sort      = id == 0
                           ? "name"
                           : "time * -1";
        SQLiteLoader retVal = new SQLiteLoader(
            this,
            new EnvelopesOpenHelper(this),
            table,
            columns,
            where,
            wArgs,
            null,
            null,
            sort
        );
        retVal.setNotificationUri(EnvelopesOpenHelper.URI);
        return retVal;
    }

    @Override public void onLoadFinished(Loader<Cursor> ldr, Cursor data) {
        if (ldr.getId() == 0) {
            mLoadedEnvelope = true;
            if (data.getCount() == 0) {
                finish();
            } else {
                data.moveToFirst();
                while (data.getInt(data.getColumnIndexOrThrow("_id")) != mId) {
                    data.moveToNext();
                }
                String name = data.getString(data.getColumnIndexOrThrow("name"));
                if (!mName.hasFocus()) {
                    if (!mName.getText().toString().equals(name)) {
                        mName.setText(name);
                    }
                    mName.setDefaultFocus(name.equals(""));
                }
                if (mDeleteAdapter.getDeletedId() == -1) {
                    long cents = data.getLong(data.getColumnIndexOrThrow("cents"));
                    mAmount.setText(EditMoney.toColoredMoney(this, cents));
                    long projected = data.getLong(
                        data.getColumnIndexOrThrow("projectedCents")
                    );
                    if (projected == cents) {
                        mProjected.setVisibility(View.GONE);
                    } else {
                        mProjected.setVisibility(View.VISIBLE);
                        mProjected.setText(
                            EditMoney.toColoredMoney(this, projected)
                        );
                    }
                }
                mColor = data.getInt(data.getColumnIndexOrThrow("color"));
                getActionBar()
                .setBackgroundDrawable(new ColorDrawable(mColor == 0 ? 0xFFEEEEEE : mColor));
                if (mNavAdapter != null) {
                    mNavAdapter.changeCursor(data);
                    if (mNavView != null) {
                        int l = mNavAdapter.getCount();
                        for (int i = 0; i != l; ++i) {
                            if (mNavAdapter.getItemId(i) == mId) {
                                mNavView.setItemChecked(i, true);
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT < 18) {
                    invalidateOptionsMenu();
                }
            }
        } else {
            mLoadedLog = true;
            if (mDeleteAdapter.getDeletedId() != -1) {
                int l = data.getCount();
                long total = 0;
                long projected = 0;
                long now = System.currentTimeMillis();
                data.moveToFirst();
                for (int i = 0; i != l; ++i) {
                    int id = data.getInt(data.getColumnIndexOrThrow("_id"));
                    if (id != mDeleteAdapter.getDeletedId()) {
                        long cents = data.getLong(data.getColumnIndexOrThrow("cents"));
                        long time = data.getLong(data.getColumnIndexOrThrow("time"));
                        if (time <= now) {
                            total += cents;
                        }
                        projected += cents;
                    }
                    data.moveToNext();
                }
                mAmount.setText(EditMoney.toColoredMoney(this, total));
                if (projected == total) {
                    mProjected.setVisibility(View.GONE);
                } else {
                    mProjected.setVisibility(View.VISIBLE);
                    mProjected.setText(
                        EditMoney.toColoredMoney(this, projected)
                    );
                }
            }
            mLogAdapter.changeCursor(data);
            final ListView lV = mLogView;
            if (lV.getLastVisiblePosition() == mLogAdapter.getCount()
                || lV.getLastVisiblePosition() <= 1) {
                lV.setBackgroundResource(R.color.cardBackground);
            } else {
                lV.setBackgroundDrawable(null);
            }
        }
    }

    @Override public void onLoaderReset(Loader<Cursor> ldr) {
        finish();
    }

    @Override public void afterTextChanged(Editable s) {
        // Do nothing.
    }

    @Override public void beforeTextChanged(CharSequence s, int start,
                                            int count, int end) {
        // Do nothing.
    }

    @Override public void onTextChanged(CharSequence s, int start,
                                        int count, int end) {
        ContentValues values = new ContentValues();
        values.put("name", s.toString());
        needDatabase().update("envelopes", values, "_id = ?", new String[] {Integer.toString(mId)});
        getContentResolver().notifyChange(EnvelopesOpenHelper.URI, null);
    }

    @Override public void onItemClick(AdapterView a, View v, int pos, long id) {
        final int iId = (int)id;
        if (a == mLogView) {
            editLogEntry(iId);
        } else if (a == mNavView) {
            switchEnvelope(iId);
        }
    }

    private void editLogEntry(int id) {
        Cursor csr = mLogAdapter.getCursor();
        int oldPos = csr.getPosition();
        csr.moveToFirst();
        int l = csr.getCount();
        for (int i = 0; i != l; ++i) {
            if (csr.getInt(csr.getColumnIndexOrThrow("_id")) == id) {
                EditTransactionFragment f = EditTransactionFragment.newInstance(
                    id,
                    csr.getString(csr.getColumnIndexOrThrow("description")),
                    csr.getLong(csr.getColumnIndexOrThrow("cents"))
                );
                f.show(getFragmentManager(), "dialog");
                break;
            }
            csr.moveToNext();
        }
        csr.moveToPosition(oldPos);
    }

    private void switchEnvelope(final int id) {
        if (!mDeleteAdapter.performDelete()) {
            mId = id;
            getLoaderManager().restartLoader(
                    0, null, EnvelopeDetailsActivity.this
            );
            getLoaderManager().restartLoader(
                1, null, EnvelopeDetailsActivity.this
            );
        } else {
            final DataSetObserver obs = new DataSetObserver() {
                @Override public void onChanged() {
                    // You can't unregister yourself in onChanged().
                    final DataSetObserver that = this;
                    mLogView.post(new Runnable() {
                        public void run() {
                            mDeleteAdapter.unregisterDataSetObserver(that);
                            switchEnvelope(id);
                        }
                    });
                }
                @Override public void onInvalidated() {
                    onChanged();
                }
            };
            mDeleteAdapter.registerDataSetObserver(obs);
        }
    }

    private void changeColor() {
        if (mColor == 0) {
            mColor = 0xFFFFC1C1;
        } else if (mColor == 0xFFFFC1C1) {
            mColor = 0xFFFF1F8F;
        } else if (mColor == 0xFFFF1F8F) {
            mColor = 0xFFFF4444;
        } else if (mColor == 0xFFFF4444) {
            mColor = 0xFFAA66CC;
        } else if (mColor == 0xFFAA66CC) {
            mColor = 0xFF33B5E5;
        } else if (mColor == 0xFF33B5E5) {
            mColor = 0xFF33E5B5;
        } else if (mColor == 0xFF33E5B5) {
            mColor = 0xFF005FDE;
        } else if (mColor == 0xFF005FDE) {
            mColor = 0xFFCE6F00;
        } else if (mColor == 0xFFCE6F00) {
            mColor = 0xFFFFBB33;
        } else {
            mColor = 0;
        }
        ContentValues values = new ContentValues();
        values.put("color", mColor);
        needDatabase().update("envelopes", values, "_id = ?", new String[] {Integer.toString(mId)});
        getContentResolver().notifyChange(EnvelopesOpenHelper.URI, null);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.envelopedetailsactivity, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent i = new Intent(this, EnvelopesActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                return true;
            case R.id.earn_menuItem:
                SpendFragment sF = SpendFragment.newInstance(mId, SpendFragment.EARN);
                sF.show(getFragmentManager(), "dialog");
                return true;
            case R.id.spend_menuItem:
                sF = SpendFragment.newInstance(mId, SpendFragment.SPEND);
                sF.show(getFragmentManager(), "dialog");
                return true;
            case R.id.color_menuItem:
                changeColor();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteThis() {
        needDatabase().delete("envelopes", "_id = ?",
                              new String[] {Integer.toString(mId)});
        needDatabase().delete("log", "envelope = ?",
                              new String[] {Integer.toString(mId)});
        getContentResolver().notifyChange(EnvelopesOpenHelper.URI, null);
    }
}
