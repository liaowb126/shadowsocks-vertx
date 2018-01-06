package shadowsocks.vertxio;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shadowsocks.crypto.CryptoException;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.SSCrypto;
import shadowsocks.util.CommonUtil;
import shadowsocks.util.LocalConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class ServerHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ServerHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;


    private Vertx mVertx;
    private NetSocket mClientSocket;
    private NetSocket mTargetSocket;
    private LocalConfig mConfig;
    private int mCurrentStage;
    private Buffer mBufferQueue;
    private SSCrypto mCrypto;

    private class Stage {
        final public static int ADDRESS = 1;
        final public static int DATA = 2;
        final public static int DESTORY = 100;
    }

    private void nextStage() {
        if (mCurrentStage != Stage.DATA){
            mCurrentStage++;
        }
    }

    //When any sockets meet close/end/exception, destory the others.
    private void setFinishHandler(NetSocket socket) {
        socket.closeHandler(v -> {
            destory();
        });
        socket.endHandler(v -> {
            destory();
        });
        socket.exceptionHandler(e -> {
            log.error("Server setFinishHandler Exception " + e.getMessage()
                    +" local " + socket.localAddress() + " , remote " + socket.remoteAddress());
            destory();
        });
    }

    public ServerHandler(Vertx vertx, NetSocket socket, LocalConfig config) {
        mVertx = vertx;
        mClientSocket = socket;
        mConfig = config;
        mCurrentStage = Stage.ADDRESS;
        mBufferQueue = Buffer.buffer();
        setFinishHandler(mClientSocket);
        try{
            mCrypto = CryptoFactory.create(mConfig.method, mConfig.password);
        }catch(Exception e){
            //Will never happen, we check this before.
        }
    }

    private Buffer compactBuffer(int start) {
        mBufferQueue = Buffer.buffer().appendBuffer(mBufferQueue.slice(start, mBufferQueue.length()));
        return mBufferQueue;
    }

    private Buffer cleanBuffer() {
        mBufferQueue = Buffer.buffer();
        return mBufferQueue;
    }

    private boolean handleStageAddress() {

        int bufferLength = mBufferQueue.length();
        String addr = null;
        int current = 0;
        // 在 addrType 前，有8个byte的校验码
        byte[] check = new byte[8];
        mBufferQueue.getBytes(0,8,check);

        long clientNow = CommonUtil.bytes2Long(check);// 用户传来的now
        long serverNow = CommonUtil.getCurrTime();// 服务器的now
        if (clientNow % CommonUtil.INTERVAL != 0
                || Math.abs(serverNow - clientNow) > 2 * CommonUtil.INTERVAL) {
            // 校验失败，分两种情况
            // 不是 INTERVAL 的倍数
            // 时间相差超过2倍的 INTERVAL
            log.error("check error : " + Arrays.toString(check));
            return true;
        } else {// 校验成功后，进一步检查IV缓存
            byte[] decrypt_iv = this.mCrypto.getIV(false);
            // 添加到缓存
            boolean flag = ClientIvCache.ins().add(decrypt_iv);
            if (!flag) {// 已经存在，添加失败
                log.error("the iv existNow ! " + Arrays.toString(decrypt_iv));
                return true;
            } else {
                // TODO
            }
        }

        // 跳过8个0
        compactBuffer(8);

        int addrType = mBufferQueue.getByte(0);

        if (addrType == ADDR_TYPE_IPV4) {
            // addrType(1) + ipv4(4) + port(2)
            if (bufferLength < 7)
                return false;
            try{
                //remote the "/"
                addr = InetAddress.getByAddress(mBufferQueue.getBytes(1, 5)).toString().substring(1);
            }catch(UnknownHostException e){
                log.error("UnknownHostException.", e);
                return true;
            }
            current = 5;
        }else if (addrType == ADDR_TYPE_HOST) {
            short hostLength = mBufferQueue.getUnsignedByte(1);
            // addrType(1) + len(1) + host + port(2)
            if (bufferLength < hostLength + 4)
                return false;
            addr = mBufferQueue.getString(2, hostLength + 2);
            current = hostLength + 2;
        }else {
            log.warn("Unsupport addr type " + addrType);
            return true;
        }
        int port = mBufferQueue.getUnsignedShort(current);
        current = current + 2;

        compactBuffer(current);
        log.info("Connecting to " + addr + ":" + port);
        connectToRemote(addr, port);
        nextStage();
        return false;
    }

    private void connectToRemote(String addr, int port) {
        // 5s timeout.
        NetClientOptions options = new NetClientOptions().setConnectTimeout(5000);
        NetClient client = mVertx.createNetClient(options);
        client.connect(port, addr, res -> {  // connect handler
            if (!res.succeeded()) {
                log.error("Failed to connect " + addr + ":" + port + ". Caused by " + res.cause().getMessage());
                destory();
                return;
            }
            mTargetSocket = res.result();
            setFinishHandler(mTargetSocket);
            mTargetSocket.handler(buffer -> { // remote socket data handler
                try {
                    byte [] data = buffer.getBytes();
                    byte [] encryptData = mCrypto.encrypt(data, data.length);
                    flowControl(mClientSocket, mTargetSocket);
                    mClientSocket.write(Buffer.buffer(encryptData));
                }catch(CryptoException e){
                    log.error("Catch exception", e);
                    destory();
                }
            });
            if (mBufferQueue.length() > 0) {
                handleStageData();
            }
        });
    }

    private void flowControl(NetSocket a, NetSocket b) {
        if (a.writeQueueFull()) {
            b.pause();
            a.drainHandler(done -> {
                b.resume();
            });
        }
    }

    private void sendToRemote(Buffer buffer) {
        flowControl(mTargetSocket, mClientSocket);
        mTargetSocket.write(buffer);
    }

    private boolean handleStageData() {
        if (mTargetSocket == null) {
            //remote is not ready, just hold the buffer.
            return false;
        }
        sendToRemote(mBufferQueue);
        cleanBuffer();

        return false;
    }

    private synchronized void destory() {
        if (mCurrentStage != Stage.DESTORY) {
            mCurrentStage = Stage.DESTORY;
        }
        if (mClientSocket != null)
            mClientSocket.close();
        if (mTargetSocket != null)
            mTargetSocket.close();
    }

    @Override
    public void handle(Buffer buffer) {
        boolean finish = false;
        try{
            byte [] data = buffer.getBytes();
            byte [] decryptData = mCrypto.decrypt(data, data.length);
            mBufferQueue.appendBytes(decryptData);
        }catch(CryptoException e){
            log.error("Catch exception", e);
            destory();
            return;
        }
        switch (mCurrentStage) {
            case Stage.ADDRESS:
                finish = handleStageAddress();
                break;
            case Stage.DATA:
                finish = handleStageData();
                break;
            default:
        }
        if (finish) {
            destory();
        }
    }
}
