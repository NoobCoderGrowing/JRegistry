package hawk.JRegitstryCore.RPC;

import lombok.Data;

@Data
public class RaftReply {

     //coomon part
     private String type;
     private long term;
     private int id;
     private boolean isLeader;

     //Appenentries reply part
     private boolean success;
     private long nextIndex;

     //Request Vote reply part
     private boolean voteGranted;
     private long lastLogIndex;
     private long lastLogTerm;
     private long voteTerm;



}
