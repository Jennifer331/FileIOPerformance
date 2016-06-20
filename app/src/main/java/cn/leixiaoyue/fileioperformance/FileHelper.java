package cn.leixiaoyue.fileioperformance;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

public class FileHelper {

    private static final String TAG = "FileHelper";
    public static final boolean HIDE_SYSTEM_FILE = true;
    public static boolean SEND_FEEDBACK = false;

    //task type
    public static final int RANDOM_READ_FILE = 0;
    public static final int IO_BUFFERED_STREAM = 1;
    public static final int IO_UN_BUFFERED_STREAM = 2;
    public static final int CHANNEL_MAPPED_BUFFERED = 3;
    public static final int DIRECT_BYTEBUFFER = 4;
    public static final int NON_DIRECT_BYTEBUFFER = 5;
    public static final int TRANSFER_TO = 6;
    public static final int TRANSFER_FROM = 7;

    public static String mapMethodName(int type){
        switch (type){
            case RANDOM_READ_FILE:
                return "RandomAccessFile read-write";
            case IO_BUFFERED_STREAM:
                return "BufferedIn(Out)putStream read-write";
            case IO_UN_BUFFERED_STREAM:
                return "FileIn(Out)putStream read-write";
            case CHANNEL_MAPPED_BUFFERED:
                return "FileChannel Map put";
            case DIRECT_BYTEBUFFER:
                return "FileChannel Direct ByteBuffer read-write";
            case NON_DIRECT_BYTEBUFFER:
                return "FileChannel ByteBuffer read-write";
            case TRANSFER_TO:
                return "FileChannel transferTo";
            case TRANSFER_FROM:
                return "FileChannel transferFrom";
        }
        return "";
    }
    /**
     * The Windows separator character.
     */
    private static final char WINDOWS_SEPARATOR = '\\';
    /**
     * The system separator character.
     */
    private static final char SYSTEM_SEPARATOR = File.separatorChar;

    private static long getDeletefilesSize;

    /**
     * The number of bytes in a kilobyte.
     */
    public static final int ONE_KB = 1024;
    /**
     * The number of bytes in a megabyte.
     */
    public static final int ONE_MB = ONE_KB * ONE_KB;
    /**
     * The file copy buffer size (16 KB)
     */
    private static final int FILE_COPY_BUFFER_SIZE = ONE_KB * 16;

    private static Statistics mStatistics = null;

    public static void initStatistics(Context context, Statistics.ChartListener listener){
        mStatistics = Statistics.getInstance(context, listener);
        SEND_FEEDBACK = true;
    }
    /**
     * @param dir
     * @throws IllegalArgumentException
     */
    private static void validateAndCheckDir(File dir) throws IllegalArgumentException {
        if (!dir.isDirectory() || !dir.exists()) {
            throw new IllegalArgumentException(
                    "The file path is must be a directory or the path is not exist.");
        }
    }

    public static String getAbsolutePath(String parent, String name) {
        if (null == parent || null == name) {
            return null;
        }
        return parent + File.separator + name;
    }

    static boolean isSystemWindows() {
        return SYSTEM_SEPARATOR == WINDOWS_SEPARATOR;
    }

    /**
     * Determines whether the specified file is a Symbolic Link rather than an
     * actual file.
     * <p/>
     * Will not return true if there is a Symbolic Link anywhere in the path,
     * only if the specific file is.
     *
     * @param file the file to check
     * @return true if the file is a Symbolic Link
     * @throws IOException if an IO error occurs while checking the file
     */
    public static boolean isSymlink(File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("File must not be null");
        }
        if (isSystemWindows()) {
            return false;
        }
        File fileInCanonicalDir = null;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Deletes a directory recursively.
     *
     * @param directory directory to delete
     * @throws IOException in case deletion is unsuccessful
     */
    public static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        if (!isSymlink(directory)) {
            cleanDirectory(directory);
        }

        getDeletefilesSize += directory.length();
        setDeletefilesSize(getDeletefilesSize);
        if (!directory.delete()) {
            String message = "Unable to delete directory " + directory + ".";
            throw new IOException(message);
        }
    }

    public static boolean delete(File file, final Context context) {
        // a file has b and c, a deleted first
        if (!file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            try {
                deleteDirectory(file);
            } catch (IOException e) {
                Log.w(TAG, "Delete file error.");
                e.printStackTrace();
                return false;

            } catch (StackOverflowError e) {
                Log.e(TAG, "delete StackOverflowError:" + e);
            }
        } else {
            getDeletefilesSize += file.length();
            setDeletefilesSize(getDeletefilesSize);
            return file.delete();
        }
        return true;
    }

    /**
     * Deletes a file. If file is a directory, delete it and all
     * sub-directories.
     * <p/>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>You get exceptions when a file or directory cannot be deleted.
     * (java.io.File methods returns a boolean)</li>
     * </ul>
     *
     * @param file file or directory to delete, must not be {@code null}
     * @throws NullPointerException  if the directory is {@code null}
     * @throws FileNotFoundException if the file was not found
     * @throws IOException           in case deletion is unsuccessful
     */
    public static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            getDeletefilesSize += file.length();
            setDeletefilesSize(getDeletefilesSize);
            if (!file.delete()) {
                if (!filePresent) {
                    throw new FileNotFoundException("File does not exist: " + file);
                }
                String message = "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }

    public static long getDeletefilesSize() {
        return getDeletefilesSize;
    }

    public static void setDeletefilesSize(long size) {
        getDeletefilesSize = size;
    }

    /**
     * Cleans a directory without deleting it.
     *
     * @param directory directory to clean
     * @throws IOException in case cleaning is unsuccessful
     */
    public static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) { // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    public static boolean rename(File src, File dest) {
        if (src != null && src.exists() && dest != null) {
            return src.renameTo(dest);
        }
        return false;

    }

    /**
     * Calculation of the specified directory contains the number of files and
     * folders.
     *
     * @param dir
     * @return if success int[0] is file count, int[1] is directory
     * count,otherwise null.
     */
    public int[] getFileAndDirCount(File dir) {
        validateAndCheckDir(dir);
        final int[] counts = new int[2];
        getFileAndDirCount(dir, counts);
        return counts;
    }

    private void getFileAndDirCount(File file, int[] counts) {
        if (!file.isDirectory()) {
            counts[0]++;
            return;
        }
        File[] list = file.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (!f.isHidden()) {
                if (f.isDirectory()) {
                    counts[1]++;
                    getFileAndDirCount(f, counts);
                } else {
                    counts[0]++;
                }
            }
        }
    }

    /**
     * checks requirements for file copy
     *
     * @param src  the source file
     * @param dest the destination
     * @return the dest file
     * @throws FileNotFoundException if the src does not exist
     */
    private static File checkFileRequirements(File src, File dest) throws FileNotFoundException {
        if (src == null) {
            throw new NullPointerException("Source must not be null");
        }
        if (dest == null) {
            throw new NullPointerException("Destination must not be null");
        }
//        if (!src.exists()) {
//            throw new FileNotFoundException("Source '" + src + "' does not exist");
//        }
        String destPath = dest.getParent();
        String name = dest.getName();
        String fileName = dest.getName().substring(0, name.lastIndexOf("."))/* + "(1)"*/;
        String fileType = dest.getName().substring(name.lastIndexOf("."));
        int index = 1;
        while (true) {
            String modifiedName = fileName + "(" + index + ")";
            File file = new File(destPath + File.separator + modifiedName + fileType);
            if (!file.exists()) {
                fileName = modifiedName;
                break;
            }
            index++;
        }
        dest = new File(destPath + File.separator + fileName + fileType);
        return dest;
    }

    /**
     * Copies a file to a new location.
     * <p/>
     * This method copies the contents of the specified source file
     * to the specified destination file.
     * The directory holding the destination file is created if it does not exist.
     * If the destination file exists, then this method will overwrite it.
     *
     * @param srcFile  an existing file to copy, must not be null
     * @param destFile the new file, must not be null
     * @throws NullPointerException if source or destination is null
     * @throws IOException          if source or destination is invalid
     * @throws IOException          if an IO error occurs during copying
     * @throws IOException          if the output file length is not the same as the input file length after the copy completes
     */
    public static void copyFile(final File srcFile, final File destFile) throws IOException {
        File realDestFile = destFile;
//        checkFileRequirements(srcFile, realDestFile);
        if (srcFile.isDirectory()) {
            throw new IOException("Source '" + srcFile + "' exists but is a directory");
        }
        if (srcFile.getCanonicalPath().equals(destFile.getCanonicalPath())) {
            throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same");
        }
        final File parentFile = destFile.getParentFile();
//        if (parentFile != null) {
//            if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
//                throw new IOException("Destination '" + parentFile + "' directory cannot be created");
//            }
//        }
        doCopyFile(srcFile, destFile);
    }

    /**
     * Internal copy file method.
     * This caches the original file length, and throws an IOException
     * if the output file length is different from the current input file length.
     * So it may fail if the file changes size.
     * It may also fail with "IllegalArgumentException: Negative size" if the input file is truncated part way
     * through copying the data and the new file size is less than the current position.
     *
     * @param srcFile  the validated source file, must not be code null
     * @param destFile the validated destination file, must not be null
     * @throws IOException              if an error occurs
     * @throws IOException              if the output file length is not the same as the input file length after the copy completes
     * @throws IllegalArgumentException "Negative size" if the file is truncated so that the size is less than the position
     */
    private static void doCopyFile(final File srcFile, final File destFile) throws IOException {
        if (destFile.exists() && destFile.isDirectory()) {
            throw new IOException("Destination '" + destFile + "' exists but is a directory");
        }

        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel input = null;
        FileChannel output = null;
        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            input = fis.getChannel();
            output = fos.getChannel();
            final long size = input.size();
            long pos = 0;
            long count = 0;
            while (pos < size) {
                final long remain = size - pos;
                count = remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain;
                final long bytesCopied = output.transferFrom(input, pos, count);
                if (bytesCopied == 0) { // can happen if file is truncated after caching the size
                    break; // ensure we don't loop forever
                }
                pos += bytesCopied;
            }
            output.force(true);
        } finally {
            IOUtils.closeQuietly(output, fos, input, fis);
        }

//        final long srcLen = srcFile.length();
//        final long dstLen = destFile.length();
//        if (srcLen != dstLen) {
//            throw new IOException("Failed to copy full contents from '" +
//                    srcFile + "' to '" + destFile + "' Expected length: " + srcLen + " Actual: " + dstLen);
//        }
    }

    public static TaskInfo transferFrom(final File srcFile, final File destFile) throws IOException {
        long startTime = System.currentTimeMillis();
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel input = null;
        FileChannel output = null;
        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            input = fis.getChannel();
            output = fos.getChannel();
            final long size = input.size();
            long pos = 0;
            long count = 0;
            while (pos < size) {
                final long remain = size - pos;
                count = remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain;
                final long bytesCopied = output.transferFrom(input, pos, count);
                if (bytesCopied == 0) { // can happen if file is truncated after caching the size
                    break; // ensure we don't loop forever
                }
                pos += bytesCopied;
            }
            output.force(true);
        } finally {
            IOUtils.closeQuietly(output, fos, input, fis);

            long duration = System.currentTimeMillis() - startTime;
            CopyTaskInfo taskInfo = new CopyTaskInfo(TRANSFER_FROM, duration, srcFile.length());
            if(SEND_FEEDBACK){
                mStatistics.addData(taskInfo);
            }
            return taskInfo;
        }
    }

    public static TaskInfo transferTo(final File srcFile, final File destFile) {
        long startTime = System.currentTimeMillis();
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel input = null;
        FileChannel output = null;
        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            input = fis.getChannel();
            output = fos.getChannel();
            final long size = input.size();
            long pos = 0;
            long count = 0;
            while (pos < size) {
                final long remain = size - pos;
                count = remain > FILE_COPY_BUFFER_SIZE ? FILE_COPY_BUFFER_SIZE : remain;
                final long bytesCopied = input.transferTo(pos, count, output);
                if (0 == bytesCopied) {
                    break;
                }
                pos += bytesCopied;
            }
            output.force(true);
        } catch (Exception e) {
            Log.e(TAG, "[transferTo]", e);
        } finally {
            IOUtils.closeQuietly(fis, fos, input, output);

            long duration = System.currentTimeMillis() - startTime;
            CopyTaskInfo taskInfo = new CopyTaskInfo(TRANSFER_TO, duration, srcFile.length());
            if(SEND_FEEDBACK){
                mStatistics.addData(taskInfo);
            }
            return taskInfo;
        }

    }

    public static TaskInfo nonDirectBuffer(final File srcFile, final File destFile){
        long startTime = System.currentTimeMillis();
        channelByteBuffer(srcFile, destFile, false);
        long duration = System.currentTimeMillis() - startTime;
        CopyTaskInfo taskInfo = new CopyTaskInfo(NON_DIRECT_BYTEBUFFER, duration, srcFile.length());
        if(SEND_FEEDBACK){
            mStatistics.addData(taskInfo);
        }
        return taskInfo;
    }

    public static TaskInfo directBuffer(final File srcFile, final File destFile){
        long startTime = System.currentTimeMillis();
        channelByteBuffer(srcFile, destFile, true);
        long duration = System.currentTimeMillis() - startTime;
        CopyTaskInfo taskInfo = new CopyTaskInfo(DIRECT_BYTEBUFFER, duration, srcFile.length());
        if(SEND_FEEDBACK){
            mStatistics.addData(taskInfo);
        }
        return taskInfo;
    }

    private static void channelByteBuffer(final File srcFile, final File destFile, boolean isDirect){
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel read = null;
        FileChannel write = null;
        try{
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            read = fis.getChannel();
            write = fos.getChannel();
            ByteBuffer buffer;
            if(isDirect){
                buffer = ByteBuffer.allocateDirect(FILE_COPY_BUFFER_SIZE);
            }else {
                buffer = ByteBuffer.allocate(FILE_COPY_BUFFER_SIZE);
            }
            while(read.read(buffer) != -1){
                buffer.flip();
                write.write(buffer);
                buffer.clear();
            }
        }catch(Exception e){
            Log.e(TAG, "[nonDirectBuffer]", e);
        }finally {
            IOUtils.closeQuietly(fis, fos, read, write);
        }
    }

    public static TaskInfo channelMappedBuffer(final File srcFile, final File destFile){
        long startTime = System.currentTimeMillis();
        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel read = null;
        FileChannel write = null;
        ByteBuffer rr = null, ww = null;
        int pos = 0;
        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            read = fis.getChannel();
            write = fos.getChannel();
            while (pos < read.size()) {
                final long remain = read.size() - pos;
                long size = remain < FILE_COPY_BUFFER_SIZE ? remain : FILE_COPY_BUFFER_SIZE;
                rr = read.map(FileChannel.MapMode.READ_ONLY, pos, size);
                ww = write.map(FileChannel.MapMode.READ_WRITE, pos, size);
                ww.put(rr);
                rr.clear();
                ww.clear();
                pos += size;
            }
        }catch(Exception e){
            Log.e(TAG, "[channelMappedBuffer]", e);
        }finally {
            IOUtils.closeQuietly(fis, fos, read, write);

            long duration = System.currentTimeMillis() - startTime;
            CopyTaskInfo taskInfo = new CopyTaskInfo(CHANNEL_MAPPED_BUFFERED, duration, srcFile.length());
            if(SEND_FEEDBACK){
                mStatistics.addData(taskInfo);
            }
            return taskInfo;
        }
    }

    public static TaskInfo ioUnBufferedStream(final File srcFile, final File destFile){
        long startTime = System.currentTimeMillis();
        FileInputStream fis = null;
        FileOutputStream fos = null;
        byte[] b = new byte[FILE_COPY_BUFFER_SIZE];
        int count;
        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            while ((count = fis.read(b)) != -1) {
                fos.write(b, 0, count);
            }
        }catch(Exception e){
            Log.e(TAG, "[ioBufferedStream]", e);
        }finally {
            IOUtils.closeQuietly(fis, fos);

            long duration = System.currentTimeMillis() - startTime;
            CopyTaskInfo taskInfo = new CopyTaskInfo(IO_UN_BUFFERED_STREAM, duration, srcFile.length());
            if(SEND_FEEDBACK){
                mStatistics.addData(taskInfo);
            }
            return taskInfo;
        }
    }

    public static TaskInfo ioBufferedStream(final File srcFile, final File destFile){
        long startTime = System.currentTimeMillis();
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        byte[] b = new byte[FILE_COPY_BUFFER_SIZE];
        int count;
        try{
            bis = new BufferedInputStream(new FileInputStream(srcFile));
            bos = new BufferedOutputStream(new FileOutputStream(destFile));
            while((count = bis.read(b)) != -1){
                bos.write(b, 0, count);
            }
        }catch(Exception e){
            Log.e(TAG, "[ioBufferedStream]", e);
        }finally {
            IOUtils.closeQuietly(bis, bos);

            long duration = System.currentTimeMillis() - startTime;
            CopyTaskInfo taskInfo = new CopyTaskInfo(IO_BUFFERED_STREAM, duration, srcFile.length());
            if(SEND_FEEDBACK){
                mStatistics.addData(taskInfo);
            }
            return taskInfo;
        }
    }

    public static TaskInfo randomReadFile(final File srcFile, final File destFile) {
        long startTime = System.currentTimeMillis();
        RandomAccessFile read = null;
        RandomAccessFile write = null;
        try {
            read = new RandomAccessFile(srcFile, "r");
            write = new RandomAccessFile(destFile, "w");
            byte[] b = new byte[FILE_COPY_BUFFER_SIZE];
            while (read.read(b) != -1) {
                write.write(b);
            }
        } catch (Exception e) {
            Log.v(TAG, "[randomReadFile]", e);
        } finally {
            IOUtils.closeQuietly(read, write);

            long duration = System.currentTimeMillis() - startTime;
            CopyTaskInfo taskInfo = new CopyTaskInfo(RANDOM_READ_FILE, duration, srcFile.length());
            if(SEND_FEEDBACK){
                mStatistics.addData(taskInfo);
            }
            return taskInfo;
        }
    }

    /**
     * Moves a file.
     * <p/>
     * When the destination file is on another file system, do a "copy and delete".
     *
     * @param srcFile  the file to be moved
     * @param destFile the destination file
     * @throws NullPointerException if source or destination is null
     * @throws IOException          if source or destination is invalid
     * @throws IOException          if an IO error occurs moving the file
     */
    public static void moveFile(final File srcFile, final File destFile) throws IOException {
        File realDestFile = destFile;
        checkFileRequirements(srcFile, realDestFile);
        if (srcFile.isDirectory()) {
            throw new IOException("Source '" + srcFile + "' is a directory");
        }
        if (realDestFile.isDirectory()) {
            throw new IOException("Destination '" + realDestFile + "' is a directory");
        }
        final boolean rename = srcFile.renameTo(realDestFile);
        if (!rename) {
            copyFile(srcFile, realDestFile);
            if (!srcFile.delete()) {
                deleteQuietly(realDestFile);
            }
        }
    }

    /**
     * Deletes a file, never throwing an exception. If file is a directory, delete it and all sub-directories.
     * The difference between File.delete() and this method are:
     * A directory to be deleted does not have to be empty.
     * No exceptions are thrown when a file or directory cannot be deleted.
     *
     * @param file file or directory to delete, can be null
     * @return true if the file or directory was deleted, otherwise false
     */
    public static boolean deleteQuietly(final File file) {
        if (file == null) {
            return false;
        }
        try {
            if (file.isDirectory()) {
                cleanDirectory(file);
            }
        } catch (final Exception ignored) {
        }

        try {
            return file.delete();
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static String getRealPathFromUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
