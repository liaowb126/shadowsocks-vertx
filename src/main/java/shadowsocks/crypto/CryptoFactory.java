package shadowsocks.crypto;

import shadowsocks.util.GlobalConfig;

public class CryptoFactory{

    public static SSCrypto create(String name, String password) throws CryptoException
    {
        return new RC4Crypto(GlobalConfig.DEFAULT_METHOD,password);
    }
}
