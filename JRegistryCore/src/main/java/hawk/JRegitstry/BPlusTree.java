package hawk.JRegitstry;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class BPlusTree {

    private BPlusNode root;

    private ReadWriteLock rdLock;

    public BPlusTree() {
        this.root = new BPlusNode("root", "~/root" );
        rdLock = new ReentrantReadWriteLock(false);
    }

    public boolean createNewNode(String key, String value){
        String[] paths = key.split("/");
        BPlusNode current = root;
        rdLock.writeLock().lock();
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){
                return false;
            }
            current = current.getChildren().get(key);
        }
        if(!current.getChildren().containsKey(paths[-1])){
            BPlusNode newNode = new BPlusNode(paths[-1], value);
            current.addNode(newNode);
            rdLock.writeLock().unlock();
            return true;
        }
        rdLock.writeLock().unlock();
        return false;
    }

    public boolean deleteNode(String key){
        String[] paths = key.split("/");
        rdLock.writeLock().lock();
        BPlusNode current = findNode(key);
        if(current!=null){
            if(!current.getChildren().containsKey(paths[-1])){
                current.getChildren().remove(paths[-1]);
                rdLock.writeLock().unlock();
                return true;
            }
        }
        rdLock.writeLock().unlock();
        return false;
    }

    public BPlusNode findNode(String key){
        String[] paths = key.split("/");
        BPlusNode current = root;
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){
                return null;
            }
            current = current.getChildren().get(key);
        }
        return current;
    }
}
