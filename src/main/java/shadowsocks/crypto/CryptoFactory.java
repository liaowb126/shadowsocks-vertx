package shadowsocks.crypto;

public class CryptoFactory{

    public static SSCrypto create(String name, String password) throws CryptoException
    {
        return new RC4Crypto(name,password);
    }
}
