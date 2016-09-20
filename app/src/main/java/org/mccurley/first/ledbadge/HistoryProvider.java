package org.mccurley.first.ledbadge;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

public class HistoryProvider extends ContentProvider {
    private static final UriMatcher uriMatcher;
    private static final int HISTORY = 1;
    private static final int HISTORY_ID = 2;
    private static final String PROVIDER_NAME = "org.mccurley.ledbadge";
    private static final String URL = "content://" + PROVIDER_NAME + "/history";
    public static final Uri CONTENT_URI = Uri.parse(URL);
    private static final String[] default_history = {
            "I hate PG&E as much as you hate Exxon",
            "My car may be ugly but it doesn't use gasoline",
            "Please wash me"
    };
    private static final String DBNAME = "historydb";
    private static final String TABLE_NAME = "history";
    public static final String MESSAGE = "message";
    public static final String LAST_MODIFIED = "last_modified";
    public static final String CREATION_TIME = "creation_time";
    private static final String SQL_CREATE = "CREATE TABLE " + TABLE_NAME +
            " (_id INTEGER PRIMARY KEY, " +
            MESSAGE + " TEXT," +
            CREATION_TIME + " INTEGER, " +
            LAST_MODIFIED + " INTEGER)";
    public static final String[] HISTORY_PROJECTION;

    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, "history", HISTORY);
        uriMatcher.addURI(PROVIDER_NAME, "history/#", HISTORY_ID);
        HISTORY_PROJECTION = new String[]{
                "_id",
                MESSAGE,
                LAST_MODIFIED,
                CREATION_TIME};
    }

    protected static final class MyDatabaseHelper extends SQLiteOpenHelper {
        MyDatabaseHelper(Context context) {
            super(context, DBNAME, null, 1);
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE);
            ContentValues values = new ContentValues();
            long timestamp = System.currentTimeMillis() / 1000;
            for (String query : default_history) {
                values.put(MESSAGE, query);
                values.put(CREATION_TIME, timestamp);
                values.put(LAST_MODIFIED, timestamp);
                timestamp += 1;
                db.insert(TABLE_NAME, null, values);
            }
        }
    }

    private MyDatabaseHelper dbHelper;

    public HistoryProvider() {
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        switch (uriMatcher.match(uri)) {
            case HISTORY:
                count = dbHelper.getWritableDatabase().delete(TABLE_NAME, selection, selectionArgs);
                break;
            case HISTORY_ID:
                String id = uri.getPathSegments().get(1);
                count = dbHelper.getWritableDatabase().delete(TABLE_NAME, "_ID=" + id +
                                (TextUtils.isEmpty(selection) ? "" : " AND (" + selection + ')'),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown uri:" + uri.toString());
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (uriMatcher.match(uri)) {
            case HISTORY:
                return "vnd:android.cursor.dir/";
            case HISTORY_ID:
                return "vnd:android.cursor.item/";
            default:
                throw new UnsupportedOperationException("Not yet implemented");
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri returnUri = ContentUris.withAppendedId(CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(returnUri, null);
            return returnUri;
        }
        throw new SQLException("Insert failed");
    }


    @Override
    public boolean onCreate() {
        dbHelper = new MyDatabaseHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return (db != null);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);
        switch (uriMatcher.match(uri)) {
            case HISTORY:
                break;
            case HISTORY_ID:
                qb.appendWhere("_ID=" + uri.getPathSegments().get(1));
                break;
            default:
                throw new IllegalArgumentException("unknown uri:" + uri.toString());
        }
        if (TextUtils.isEmpty(sortOrder)) {
            sortOrder = LAST_MODIFIED + " DESC";
        }
        Cursor c = qb.query(dbHelper.getReadableDatabase(),
                projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        switch (uriMatcher.match(uri)) {
            case HISTORY:
                count = dbHelper.getWritableDatabase().update(TABLE_NAME, values, selection, selectionArgs);
                break;
            case HISTORY_ID:
                count = dbHelper.getWritableDatabase().update(TABLE_NAME, values,
                        "_ID=" + uri.getPathSegments().get(1) +
                                (TextUtils.isEmpty(selection) ? "" : " AND (" + selection + ')'),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("unknown uri:" + uri.toString());
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
