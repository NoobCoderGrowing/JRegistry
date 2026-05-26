package hawk.JRegitstryCore;

import java.util.Arrays;
import hawk.JRegitstryCore.Log.LogEntry;
import java.util.concurrent.ThreadPoolExecutor;
import com.alibaba.fastjson.JSON;
import java.io.FileOutputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class StateMachine {

    private BPlusNode root;


    public StateMachine() {
        this.root = new BPlusNode("root", "/root" );
    }

    public boolean putIfAbsent(String key, byte[] value, String type){
        String[] paths = key.split("\\.");
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
        if(current.getChildren().containsKey(paths[paths.length-1])){// if the node is already exists, return false
            return false;
        }else{// if the node is not exists, add it
            BPlusNode newNode = new BPlusNode(paths[paths.length-1], value, type);
            current.addNode(newNode);
            newNode.setValue(value);
            newNode.setType(type);
            return true;
        }
    }

    public boolean put(LogEntry logEntry){
        String key = logEntry.getKey();
        byte[] value = logEntry.getData();
        String type = logEntry.getDataType();

        String[] paths = key.split("\\.");
        BPlusNode current = root;
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){ 
                current = current.getChildren().get(paths[i]);
            }else{ // if the node is not exists, add it
                // String path = String.join("/", Arrays.copyOfRange(paths, 0, i+1));
                // BPlusNode newNode = new BPlusNode(paths[i], path);
                BPlusNode newNode = new BPlusNode(paths[i]);
                current.addNode(newNode);
                current = newNode;
            }
        }

        BPlusNode newNode = new BPlusNode(paths[paths.length-1], value, type);
        current.addNode(newNode);
        log.info("current node info: {}", current.show());
        return true;
    }


    public Pair<String, byte[]> get(String key){
        String[] paths = key.split("\\.");
        BPlusNode current = root;
        for (int i = 0; i < paths.length; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{
                return null;
            }
        }
        return new Pair<String, byte[]>(current.getType(), current.getValue());
    }

    public boolean delete(String key){
        String[] paths = key.split("\\.");
        BPlusNode current = root;
        for (int i = 0; i < paths.length-1; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{
                return false;
            }
        }
        if(current.getChildren().containsKey(paths[paths.length-1])){
            current.deleteNode(paths[paths.length-1]);
            return true;
        }else{
            return false;
        }
    }

    public boolean persist(ThreadPoolExecutor writePool){
        String serializedTree = JSON.toJSONString(this);
        writePool.execute(() -> {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream("lsmTree.json");
                fileOutputStream.write(serializedTree.getBytes());
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    public boolean applyLog(LogEntry logEntry){
        String cmd = logEntry.getCommand();
        log.info("apply log: {}", JSON.toJSONString(logEntry));
        boolean success = false;
        switch (cmd) {
            case "set":
                log.info("into set");
                success = put(logEntry);
                break;
            case "delete":
                success = delete(logEntry.getKey());
                break;
            case "noOp":
                success = true;
                break;
            default:
                break;
        }
        return success;
    }

    public BPlusNode getRoot(){
        return root;
    }

    public BPlusNode cd(String path, BPlusNode position){
        if(path.equals("/")||path.equals("/root")||path.equals("~")){
            return root;
        }

        if(path.equals("..")){
            if(position!=root){
                return position.getParent();
            }
            return root;
        }

        if(path.charAt(0)!='/' && path.charAt(0)!='~'){
            BPlusNode current = position.cd(path);
            return current;
        }


        String[] paths = path.split("\\.");
        BPlusNode current = root;
        for (int i = 0; i < paths.length; i++) {
            if(current.getChildren().containsKey(paths[i])){
                current = current.getChildren().get(paths[i]);
            }else{
                return null;
            }
        }
        return current;
    }


    public void rebuildParentLinks() {
        rebuildParent(root, null);
    }

    private void rebuildParent(BPlusNode node, BPlusNode parent) {
        if (node == null) return;
        node.setParent(parent);
        if (node.getChildren() != null) {
            for (BPlusNode child : node.getChildren().values()) {
                rebuildParent(child, node);
            }
        }
    }


   
public static void main(String[] args) {
    String[] paths = "we.n".split("\\.");
    // String[] paths = "we.n".split("\\.");
    System.out.println(paths[-1]);
}

}
