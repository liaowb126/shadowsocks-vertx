package shadowsocks.crypto;

public class CryptoFactory{

    public static SSCrypto create(String name, String password) throws CryptoException
    {
        return new RC4MD5Crypto(name,password);
    }
}
