package hawk.JRegitstryCore;

public interface LSMTree {

    public boolean put(String key, byte[] value, String type);
    public String get(String key);
    public boolean delete(String key);
    public boolean persist();

}