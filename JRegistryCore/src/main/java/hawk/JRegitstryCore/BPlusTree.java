package hawk.JRegitstryCore;

import java.util.Arrays;
import hawk.JRegitstryCore.Log.LogEntry;

public class BPlusTree implements LSMTree {

    private BPlusNode root;

    public BPlusTree() {
        this.root = new BPlusNode("root", "~/root" );
    }

    public boolean put(String key, byte[] value, String type){
        String[] paths = key.split(".");
        BPlusNode current = root;
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
            return false;
        }else{// if the node is not exists, add it
            BPlusNode newNode = new BPlusNode(paths[-1], value, type);
            current.addNode(newNode);
            newNode.setValue(value);
            newNode.setType(type);
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

    public void applyLog(LogEntry logEntry){
        return;
    }
}
