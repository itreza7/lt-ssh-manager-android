package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session backed by an EXTERNAL byte-stream pair (an SSH shell channel) instead of a
 * local pseudo-terminal.
 * <p>
 * The upstream Termux implementation spawned a local process over a JNI pty. This port removes the
 * JNI/pty entirely (CLAUDE.md §7.1): the {@link TerminalEmulator} is fed from {@link #mInputStream}
 * (remote stdout) and {@link #write} forwards to {@link #mOutputStream} (remote stdin). Window-size
 * changes are delegated to a {@link PtyResizeHandler} which the SSH layer implements via
 * {@code Session.changeWindowDimensions(...)}.
 * <p>
 * All terminal emulation and client callbacks happen on the main thread.
 */
public final class TerminalSession extends TerminalOutput {

    /** Notified when the terminal window size changes so the SSH layer can resize the remote PTY. */
    public interface PtyResizeHandler {
        void onResize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);
    }

    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_REMOTE_CLOSED = 4;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /** Written by the reader thread (remote -> here), drained by the main thread into the emulator. */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(64 * 1024);
    /** Written by the main thread (user input), drained by the writer thread to the remote stdin. */
    final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);
    private final byte[] mUtf8InputBuffer = new byte[5];

    TerminalSessionClient mClient;

    /** Set by the application for user identification of session, not by terminal. */
    public String mSessionName;

    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private final PtyResizeHandler mResizeHandler;
    private final Integer mTranscriptRows;

    private volatile boolean mRunning = true;

    final Handler mMainThreadHandler = new MainThreadHandler();

    public TerminalSession(InputStream remoteStdout, OutputStream remoteStdin, Integer transcriptRows,
                           TerminalSessionClient client, PtyResizeHandler resizeHandler) {
        this.mInputStream = remoteStdout;
        this.mOutputStream = remoteStdin;
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
        this.mResizeHandler = resizeHandler;
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        if (mEmulator != null) mEmulator.updateTerminalSessionClient(client);
    }

    /** Inform the remote PTY of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            if (mResizeHandler != null) mResizeHandler.onResize(columns, rows, cellWidthPixels, cellHeightPixels);
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    /** Create the emulator and start the reader/writer threads bridging the remote streams. */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        if (mResizeHandler != null) mResizeHandler.onResize(columns, rows, cellWidthPixels, cellHeightPixels);

        new Thread("TermSessionInputReader") {
            @Override
            public void run() {
                final byte[] buffer = new byte[4096];
                try {
                    while (mRunning) {
                        int read = mInputStream.read(buffer);
                        if (read == -1) break;
                        if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) break;
                        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
                    }
                } catch (Exception e) {
                    // Ignore, just shutting down.
                }
                mMainThreadHandler.sendEmptyMessage(MSG_REMOTE_CLOSED);
            }
        }.start();

        new Thread("TermSessionOutputWriter") {
            @Override
            public void run() {
                final byte[] buffer = new byte[4096];
                try {
                    while (mRunning) {
                        int bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true);
                        if (bytesToWrite == -1) break;
                        mOutputStream.write(buffer, 0, bytesToWrite);
                        mOutputStream.flush();
                    }
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }.start();
    }

    /** Write data to the remote shell (stdin). */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (mRunning) mTerminalToProcessIOQueue.write(data, offset, count);
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }
        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;
        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }

    public void reset() {
        if (mEmulator != null) {
            mEmulator.reset();
            notifyScreenUpdate();
        }
    }

    /** Finish this terminal session by closing the remote streams. */
    public void finishIfRunning() {
        if (mRunning) {
            mRunning = false;
            mTerminalToProcessIOQueue.close();
            mProcessToTerminalIOQueue.close();
            try { mInputStream.close(); } catch (IOException ignored) { }
            try { mOutputStream.close(); } catch (IOException ignored) { }
        }
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    public synchronized boolean isRunning() {
        return mRunning;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {
        final byte[] mReceiveBuffer = new byte[64 * 1024];

        MainThreadHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            int bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
            if (bytesRead > 0 && mEmulator != null) {
                mEmulator.append(mReceiveBuffer, bytesRead);
                notifyScreenUpdate();
            }
            if (msg.what == MSG_REMOTE_CLOSED) {
                if (mEmulator != null) {
                    byte[] bytes = "\r\n[Session closed]".getBytes(StandardCharsets.UTF_8);
                    mEmulator.append(bytes, bytes.length);
                    notifyScreenUpdate();
                }
                mClient.onSessionFinished(TerminalSession.this);
            }
        }
    }
}
