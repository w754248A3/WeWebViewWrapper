package com.wewebviewwrapper;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MyDocumentsProvider extends DocumentsProvider {
    private static final String AUTHORITY = "com.wewebviewwrapper.provider";
    private static final String ROOT_ID = "root";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_SUMMARY
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE |
                DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(DocumentsContract.Root.COLUMN_TITLE, "Private Storage");
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(getContext().getFilesDir()));
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "App's private storage");
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        includeFile(result, documentId, null);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        final File parent = getFileForDocId(parentDocumentId);
        File[] files = parent.listFiles();
        if (files != null) {
            for (File file : files) {
                includeFile(result, null, file);
            }
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        File parent = getFileForDocId(parentDocumentId);
        File file = new File(parent, displayName);
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!file.mkdirs()) throw new IOException("Failed to create directory");
            } else {
                if (!file.createNewFile()) throw new IOException("Failed to create file");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to create document: " + e.getMessage());
        }
        return getDocIdForFile(file);
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        if (!deleteRecursive(file)) {
            throw new FileNotFoundException("Failed to delete document");
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        File newFile = new File(file.getParentFile(), displayName);
        if (!file.renameTo(newFile)) {
            throw new FileNotFoundException("Failed to rename document");
        }
        return getDocIdForFile(newFile);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            File parent = getFileForDocId(parentDocumentId);
            File child = getFileForDocId(documentId);
            return child.getAbsolutePath().startsWith(parent.getAbsolutePath());
        } catch (FileNotFoundException e) {
            return false;
        }
    }

    private String getDocIdForFile(File file) {
        String path = file.getAbsolutePath();
        String baseDir = getContext().getFilesDir().getAbsolutePath();
        if (path.startsWith(baseDir)) {
            path = path.substring(baseDir.length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path.isEmpty() ? ROOT_ID : path;
    }

    private File getFileForDocId(String documentId) throws FileNotFoundException {
        File baseDir = getContext().getFilesDir();
        if (ROOT_ID.equals(documentId) || documentId.isEmpty()) {
            return baseDir;
        }
        File file = new File(baseDir, documentId);
        if (!file.exists()) throw new FileNotFoundException("File not found: " + documentId);
        return file;
    }

    private void includeFile(MatrixCursor result, String documentId, File file) throws FileNotFoundException {
        if (file == null) {
            file = getFileForDocId(documentId);
        } else {
            documentId = getDocIdForFile(file);
        }

        int flags = 0;
        if (file.isDirectory()) {
            flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
        }
        flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
        flags |= DocumentsContract.Document.FLAG_SUPPORTS_RENAME;

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length());
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, getTypeForFile(file));
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
    }

    private String getTypeForFile(File file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        } else {
            return getTypeForName(file.getName());
        }
    }

    private String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
}
