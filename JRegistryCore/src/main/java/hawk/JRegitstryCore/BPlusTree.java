package hawk.JRegitstryCore;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Arrays;

public class BPlusTree implements LSMTree {

    private BPlusNode root;

    private ReadWriteLock rdLock;

    public BPlusTree() {
        this.root = new BPlusNode("root", "~/root" );
        rdLock = new ReentrantReadWriteLock(false);
    }

    public boolean put(String key, byte[] value, String type){
        String[] paths = key.split(".");
        BPlusNode current = root;
        rdLock.writeLock().lock();
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){ 
                current = current.getChildren().get(paths[i]);
            }else{ // if the node is not exists, add it
                String path = String.join("/", Arrays.copyOfRange(paths, 0, i+1));
                BPlusNode newNode = new BPlusNode(paths[i], path);
                current.addNode(newNode);
                current = newNode;
            }
        }
        if(current.getChildren().containsKey(paths[-1])){// if the node is already exists, return false
            rdLock.writeLock().unlock();
            return false;
        }else{// if the node is not exists, add it
            BPlusNode newNode = new BPlusNode(paths[-1], value, type);
            current.addNode(newNode);
            newNode.setValue(value);
            newNode.setType(type);
            rdLock.writeLock().unlock();
            return true;
        }
}

    public String get(String key){

        return null;
    }

    public boolean delete(String key){
        return false;
    }

    public boolean persist(){
        return false;
    }
}
