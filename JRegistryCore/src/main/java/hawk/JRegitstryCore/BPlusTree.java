package hawk.JRegitstryCore;


import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BPlusTree {

    private BPlusNode root;

    private ReadWriteLock rdLock;

    public BPlusTree() {
        this.root = new BPlusNode("root", "~/root" );
        rdLock = new ReentrantReadWriteLock(false);
    }

    public boolean create(String key, Map<String,Object> values){
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
            BPlusNode newNode = new BPlusNode(paths[-1], values);
            current.addNode(newNode);
            rdLock.writeLock().unlock();
            return true;
        }
        return false;
    }
}
