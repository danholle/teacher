package com.danholle.teacher;

//
// ----------------------------------------------------------------
//
//                  Question Answering System
//
//   case independent answer name lookup
//   "goodbye" answer... mandatory, used if no more material to offer
//                                           
// ----------------------------------------------------------------
//
 
import java.awt.*;
import java.sql.*;
import java.lang.*;
import java.lang.Double;
import java.util.*;
import java.text.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.apache.xalan.serialize.*;
import javax.xml.transform.*;
import java.io.*;
import java.awt.image.*;
//import com.sun.image.codec.jpeg.*;
import javax.swing.*;
import java.lang.reflect.*;
import java.net.*;
import javax.servlet.*;


public class chatengine {
  static final String DELIMS=" ,.-?!;:/()\"";

  // Operational parameters passed when we start
  boolean logging; // if true, all questions are logged
  ServletConfig config;                                                    
  String book;   // file name of The Book


  // Stuff associated with The Book
  Document doc;   // XML
  Element root;


  // Bot specific values set as <book> attributes
  String botname; // e.g. The Virtual Sales Guy
  double ambig; // match is ambig if >1 with prob>ambig*max, e.g. 0.8
  double thresh; // match isn't strong if prob<thresh/#ans, e.g. 1.5

  // Info extracted from The Book
  int qcnt;          // number of questions
  int acnt;          // number of answers
  int wcnt;          // number of known words
  int[] qa;          // qa[i] is ans # for question i
  int[] aqcnt;       // aqcnt[j] is # of questions having ans j
  boolean[] arpt;    // arpt[j] is true if we can repeat answer j
  boolean[] aact;    // aact[j] is true if ans j avail in all contexts
  boolean[] alast;   // alast[j] is true answer j ends the conversation
  int[][] qnw;       // qnw[i][n] is word# for word n of ques #i
  String[] aname;    // aname[j] is name of answer j
  String[] about;    // about[j] is external "topic" for answer j
  int[] aess;        // aess[j] is essense topic for j... ie k if j incl's k
  Element[] aen; // aen[j] is Element for answer j
  boolean[] wok;     // wok[k] is true if word k is a keyword
  int keysymcnt;     // number of distinct words used in the model
                     //   = count of true wok[k] + true phrkey[n]
  int[][] wacnt;     // wacnt[k][j]=# times symbol k occurs
                     //   in q's for ans j
  int[] awcnt;       // awcnt[j] is number of valid symbol posits
                     //   in q's for ans j
  String[] wstr;     // wstr[k] is string for word k
  HashMap<String,Integer> mapw;  // mapw.get(txt) returns 
                                 // Integer(word#)
  int[][] anext;     // anext[j][n] is the nth guy in the next list for j
  boolean[] aofr;    // aofr[j] is true (the default) if it's reasonable 
                     //   to offer this answer as a talking point
  int[][] syn;       // syn[j][n] is the nth synonym's a# for answer j 
  int[] ltops;       // Lead topic a#'s, highest prio first
  int phrcnt;        // number of key/noise phrases 
  int[][] phr;       // phr[i][j] is word j of key/noise phrase i 
  boolean[] phrkey;  // phrkey[i] is true if phrase i is key phrase (not noise)
  int[] phrlen;      // phrlen[i] is # words in shortest phrase matching phr[i]
                                               
  // Constructor
  chatengine(ServletConfig cfg, String book, boolean logging) {
    this.book=book;
    config=cfg;
    this.logging=logging;

    try { // Try reading xml
      // If XML exists, pick it up.
      // If not, create empty XML document
      String fn=cfg.getServletContext().getRealPath(
        File.separator+"WEB-INF"+File.separator+book);
      File f=new File(fn);

      if (f.exists()) {
        DocumentBuilderFactory xmlfactory=
          DocumentBuilderFactory.newInstance();
        DocumentBuilder xmlbuilder=xmlfactory.newDocumentBuilder();
        doc=xmlbuilder.parse(f);
        root=(Element)doc.getDocumentElement();
      } // read the XML
      else {
        System.out.println(now()+" Unable to find XML file "+book);
        System.exit(1);
      }
    } // try reading XML
    catch (Exception e) {
      System.out.println(now()+" Error reading XML: "+e);
      System.exit(1);
    } // catch


    botname=root.getAttribute("botname");
    if ((botname==null)||botname.equals(""))
      botname="The Bot";

    String ambigstr=root.getAttribute("ambig");
    if ((ambigstr==null)||ambigstr.equals("")) ambig=0.80;
    else ambig=Double.parseDouble(ambigstr);
                      
    String threshstr=root.getAttribute("thresh");
    if ((threshstr==null)||threshstr.equals("")) thresh=1.5;
    else thresh=Double.parseDouble(threshstr);
                      
    // Take an initial pass over The Book
    //   vector: questions
    //   map: answer name to answer XML
    Vector<Element> quesvect=new Vector<Element>();

    HashMap<String,Integer> ansmap=new HashMap<String,Integer>();
    ansmap.put("next",new Integer(0));

    Vector<Element> ansvect=new Vector<Element>();
    ansvect.addElement(null); // "next" answer is answer 0

    Vector noisevect=new Vector();

    // Key phrase, noise phrase
    Vector<String> phrvect=new Vector<String>();
    Vector<Boolean> typvect=new Vector<Boolean>();
               
    Node nod=root.getFirstChild();
    Node ltn=null;  // leadtopics
    Node noin=null; // noise words
    Node keyn=null; // keywords
    while (nod!=null) {
      if (nod.getNodeName().equals("question")) {
        Element qen=(Element)nod;
        quesvect.addElement(qen);
      } // if question node

      if (nod.getNodeName().equals("answer")) {
        Element ansen=(Element)nod;
        String name=ansen.getAttribute("topic");
        if (name==null) {
          System.out.println(now()+" Answer found with no name!  Shit!");
          System.exit(1);
        }

        Integer oldint=ansmap.get(name);
        if (oldint==null) ansmap.put(name,new Integer(ansvect.size()));
        else {
          System.out.println(now()+" Multiple definitions of answer \""
            +name+"\"!");
          System.exit(1);
        }
        ansvect.addElement(ansen);
      } // if answer node


      if (nod.getNodeName().equals("leadtopics")) {
        if (ltn==null) ltn=nod;
        else {
          System.out.println(now()+" Multiple <leadtopics> nodes?!");
          System.exit(1);
        }
      }

      if (nod.getNodeName().equals("noisewords")) {
        if (noin==null) noin=nod;
        else {
          System.out.println(now()+" Multiple <noisewords> nodes?!");
          System.exit(1);
        }
      } // <noisewords>

      if (nod.getNodeName().equals("keywords")) {
        if (keyn==null) keyn=nod;
        else {
          System.out.println(now()+" Multiple <keywords> nodes?!");
          System.exit(1);
        }
      } // <keywords>

      boolean iskey=nod.getNodeName().equals("key");
      boolean isnoise=nod.getNodeName().equals("noise");
      if (iskey||isnoise) {
        Element pen=(Element)nod;
        String phrase=pen.getAttribute("phrase");
        if (phrase==null) phrase="";
        phrase=phrase.trim().toUpperCase();
        if (phrase.equals("")) {
          if (iskey) System.out.println(now()+" Empty <key phrase>");
          if (isnoise) System.out.println(now()+" Empty <noise phrase>");
          System.exit(1);
        }
        phrvect.addElement(phrase);
        typvect.addElement(new Boolean(iskey)); 
      }

      nod=nod.getNextSibling();                   
    }



    // Second pass over question nodes, getting
    //   Array:  ans# for each question
    //   Map:    question word to word#
    //   Array:  word# for each <question#,word#>
    //   Array:  word text for each word#
    qcnt=quesvect.size();
    qa=new int[qcnt];
    mapw=new HashMap<String,Integer>();
    qnw=new int[qcnt][];
    Vector<String> wordtext=new Vector<String>(); // text for each 
                                                  // unique word
    for (int i=0;i<qcnt;i++) {
      Element qen=(Element)quesvect.elementAt(i);

      String ans=qen.getAttribute("topic");
      if (ans==null) {
        System.out.println(now()+" Question "+i+" has no topic attribute!");
        System.exit(1);
      }
          
      Integer ano=(Integer)ansmap.get(ans);
      if (ano==null) {
        System.out.println(now()+" Answer \""+ans+"\" (in question "+i
          +") is not defined!");
        System.exit(1);
      }
      qa[i]=ano.intValue();
                      
      Node tn=qen.getFirstChild();
      String qtext="";
      while (tn!=null) {
        qtext+=" ";
        if (tn.getNodeName().equals("#text"))
          qtext+=" "+tn.getNodeValue();
        tn=tn.getNextSibling();
      }

      StringTokenizer st=new StringTokenizer(qtext,DELIMS);
      Vector<Integer> wordnums=new Vector<Integer>(); // word number
                                              // sequence for this q
      while (st.hasMoreTokens()) {
        String s=st.nextToken().trim().toUpperCase();
        if ((s.length()>0)&&(DELIMS.indexOf(s)<0)) {
          // "rootify" the word
          if (s.length()>=7) s=s.substring(0,6)+"*";

          // Remove 's 't 'm 've 'll sorts of things
          int qpos=s.indexOf("'");
          if ((qpos+3>=s.length())&&(qpos>0))
            s=s.substring(0,qpos);

          Integer wno=(Integer)mapw.get(s);
          if (wno==null) {
            wno=new Integer(wordtext.size());
            mapw.put(s,wno);
            wordtext.addElement(s);
          }
          wordnums.addElement(wno);
        }
      } // while more tokens to look at    

      qnw[i]=new int[wordnums.size()];
      for (int j=0;j<wordnums.size();j++)
        qnw[i][j]=wordnums.elementAt(j).intValue();
    } // for question i


    // Look at the <noisewords> he specified
    Vector<Integer> noiv=null;
    if (noin==null) {
      System.out.println(now()+" No <noisewords> words specified.");
      //System.exit(1);
    }
    else {
      String noises="";
      noin=noin.getFirstChild();
      while (noin!=null) {
        if (noin.getNodeName().equals("#text"))
          noises+=" "+noin.getNodeValue();
        noin=noin.getNextSibling();
      }

      StringTokenizer st=new StringTokenizer(noises,DELIMS);
      noiv=new Vector<Integer>();
      while (st.hasMoreTokens()) {
        String ftok=st.nextToken();
        ftok=ftok.trim().toUpperCase();
        if (!ftok.equals("")&&(DELIMS.indexOf(ftok)<0)) {
          if (ftok.length()>=7) ftok=ftok.substring(0,6)+"*";

          // Remove 's 't 'm 've 'll sorts of things
          int qpos=ftok.indexOf("'");
          if ((qpos+3>=ftok.length())&&(qpos>0))
            ftok=ftok.substring(0,qpos);

          Integer wno=(Integer)mapw.get(ftok);
          if (wno==null) {
            wno=new Integer(wordtext.size());
            mapw.put(ftok,wno);
            wordtext.addElement(ftok);
          }
          noiv.addElement(wno);
        }      
      } // while picking off tokens
      System.out.println(now()+" "+noiv.size()+" noise words defined.");
    } // handle <noisewords>  
                            
    // Look at the <keywords> words he specified
    Vector<Integer> keyv=null;
    if (keyn==null) {
      System.out.println(now()+" No <keywords> words specified.");
      //System.exit(1);
    }
    else {
      String keys="";
      keyn=keyn.getFirstChild();
      while (keyn!=null) {
        if (keyn.getNodeName().equals("#text"))
          keys+=" "+keyn.getNodeValue();
        keyn=keyn.getNextSibling();
      }

      StringTokenizer st=new StringTokenizer(keys,DELIMS);
      keyv=new Vector<Integer>();
      while (st.hasMoreTokens()) {
        String ftok=st.nextToken();
        ftok=ftok.trim().toUpperCase();
        if (!ftok.equals("")&&(DELIMS.indexOf(ftok)<0)) {
          if (ftok.length()>=7) ftok=ftok.substring(0,6)+"*";

          // Remove 's 't 'm 've 'll sorts of things
          int qpos=ftok.indexOf("'");
          if ((qpos+3>=ftok.length())&&(qpos>0))
            ftok=ftok.substring(0,qpos);

          Integer wno=(Integer)mapw.get(ftok);
          if (wno==null) {
            wno=new Integer(wordtext.size());
            mapw.put(ftok,wno);
            wordtext.addElement(ftok);
          }
          keyv.addElement(wno);
        }      
      } // while picking off tokens
      System.out.println(now()+" "+keyv.size()+" keywords defined.");
    } // handle <keywords>

                             
    // Handle any <key phrase> or <noise phrase>'s
    phrcnt=phrvect.size();
    phr=new int[phrcnt][];
    phrkey=new boolean[phrcnt];
    phrlen=new int[phrcnt];
    for (int i=0;i<phrcnt;i++) {
      String phrase=(String)phrvect.elementAt(i);
      StringTokenizer st=new StringTokenizer(phrase,DELIMS);
      Vector<Integer> pv=new Vector<Integer>();
      int mincnt=0;
      while (st.hasMoreTokens()) {
        String ftok=st.nextToken();
        ftok=ftok.trim().toUpperCase();
        if (!ftok.equals("")&&(DELIMS.indexOf(ftok)<0)) {
          if (ftok.length()>=7) ftok=ftok.substring(0,6)+"*";

          // Remove 's 't 'm 've 'll sorts of things
          int qpos=ftok.indexOf("'");
          if ((qpos+3>=ftok.length())&&(qpos>0))
            ftok=ftok.substring(0,qpos);

          if (!ftok.equals("*")) mincnt++;

          Integer wno=(Integer)mapw.get(ftok);
          if (wno==null) {
            wno=new Integer(wordtext.size());
            mapw.put(ftok,wno);
            wordtext.addElement(ftok);
          }
          pv.addElement(wno);
        }      
      } // while picking off tokens
      phr[i]=new int[pv.size()];
      phrlen[i]=mincnt;
      phrkey[i]=((Boolean)typvect.elementAt(i)).booleanValue();
      for (int j=0;j<pv.size();j++)
        phr[i][j]=((Integer)pv.elementAt(j)).intValue();
    }

    // Sort key phrases to facilitate "parsing"
    for (int i=0;i<phr.length-1;i++)
      for (int j=i+1;j<phr.length;j++)
        if ((phrlen[i]<phrlen[j])
        ||((phrlen[i]==phrlen[j])&&phrkey[j]&&!phrkey[i])) {
          int[] temp=phr[i];
          phr[i]=phr[j];
          phr[j]=temp;
          boolean tmp=phrkey[i];
          phrkey[i]=phrkey[j];
          phrkey[j]=tmp;
          int itmp=phrlen[i];
          phrlen[i]=phrlen[j];
          phrlen[j]=itmp;
        }
                       
    // Create lookup array mapping word# to word text
    wcnt=wordtext.size();
    wstr=new String[wcnt];
    for (int i=0;i<wcnt;i++)
      wstr[i]=(String)wordtext.elementAt(i);
    wordtext=null;

    // Second pass over answers    
    acnt=ansvect.size();
    aen=new Element[acnt];
    aname=new String[acnt];
    about=new String[acnt];
    aofr=new boolean[acnt];
    aess=new int[acnt];
    anext=new int[acnt][];
    arpt=new boolean[acnt];     
    alast=new boolean[acnt];
    syn=new int[acnt][];
    aact=new boolean[acnt];
     



    // The virtual answer "next"
    aname[0]="next";
    aen[0]=null;
    aofr[0]=false;
    aess[0]=0;
    arpt[0]=true;
    aact[0]=true;
    alast[0]=false;



    for (int i=1;i<acnt;i++) {
      aen[i]=(Element)ansvect.elementAt(i);
      aname[i]=aen[i].getAttribute("topic");
      about[i]=aen[i].getAttribute("about");
      aess[i]=i;

      // Default is offer=yes
      // Means we are allowed to offer this answer topic to the user.
      String examp=aen[i].getAttribute("offer");
      if (examp==null) examp="";
      examp=examp.trim().toUpperCase();
      if (examp.equals("NO")||examp.equals("FALSE")
      ||examp.equals("OFF")||examp.equals("0")) aofr[i]=false;
      else aofr[i]=true;

      // Default is repeat=no                     
      // Means we are not allowed to repeat this answer
      String ar=aen[i].getAttribute("repeat");
      if (ar==null) ar="";
      ar=ar.trim().toUpperCase();
      if (ar.equals("YES")||ar.equals("TRUE")
      ||ar.equals("ON")||ar.equals("1")) arpt[i]=true;
      else arpt[i]=false;
           
      // Default is last=no
      // Means this answer does not end the conversation
      String last=aen[i].getAttribute("last");
      if (last==null) last="";
      last=last.trim().toUpperCase();
      if (last.equals("YES")||last.equals("TRUE")
      ||last.equals("ON")||last.equals("1")) alast[i]=true;
      else alast[i]=false;


      if (about[i]==null) {
        System.out.println(now()+" Missing 'topic' attribute on answer "
          +aname[i]+"!");
        System.exit(1);
      }
    }


    // Look at the <leadtopics> he specified
    if (ltn==null) {
      System.out.println(now()+" No <leadtopics> specified.");
      System.exit(1);
    }
    else {
      String leads="";
      ltn=ltn.getFirstChild();
      while (ltn!=null) {
        if (ltn.getNodeName().equals("#text"))
          leads+=" "+ltn.getNodeValue();
        ltn=ltn.getNextSibling();
      }

      StringTokenizer st=new StringTokenizer(leads,DELIMS);
      Vector<Integer> tops=new Vector<Integer>();
      while (st.hasMoreTokens()) {
        String ftok=st.nextToken();
        ftok=ftok.trim();
        if (!ftok.equals("")&&(DELIMS.indexOf(ftok)<0)) {
          Integer finteger=(Integer)ansmap.get(ftok);
          if (finteger==null) {
            System.out.println(now()+" <leadtopics> contains undefined topic "
              +ftok);
            System.exit(1);
          }
          tops.addElement(finteger);
        }
      } // while picking off tokens
    
      for (int j=1;j<acnt;j++) aact[j]=false;

      ltops=new int[tops.size()];
      for (int j=0;j<tops.size();j++) {
        ltops[j]=((Integer)tops.elementAt(j)).intValue();
        // System.out.println("j="+j+", acnt="+acnt+", ltops[j]="+ltops[j]);
        if (aact[ltops[j]]) {
          System.out.println(now()+" Answer topic "+aname[ltops[j]]
            +" in <leadtopics> more than once.");
          System.exit(1);
        }
        aact[ltops[j]]=true;
      }

      String cons="";
      System.out.println(now()+" These topics require context:");
      for (int j=0;j<acnt;j++)
        if (!aact[j]) {
          if (cons.length()+aname[j].length()+1>50) {
            System.out.println(now()+"   "+cons);
            cons=" "+aname[j];
          }
          else cons+=" "+aname[j];
        }
      if (cons.equals("")) cons="None!";
      System.out.println(now()+"   "+cons);
    } // handle lead topics

    // Second pass over answers
    for (int i=1;i<acnt;i++) {
      // Is "next" specified?  If so, fill in aap accordingly
      String follow=aen[i].getAttribute("next");
      if (follow==null) follow="";
      if (!follow.equals("")) {
        StringTokenizer st=new StringTokenizer(follow,DELIMS);
        Vector<Integer> fols=new Vector<Integer>();
        while (st.hasMoreTokens()) {
          String ftok=st.nextToken();
          ftok=ftok.trim();
          if (!ftok.equals("")&&(DELIMS.indexOf(ftok)<0)) {
            Integer finteger=(Integer)ansmap.get(ftok);
            if (finteger==null) {
              System.out.println(now()+" Answer "+aname[i]+" has next="
                +ftok+" (undefined)");
              System.exit(1);
            }
            fols.addElement(finteger);
          }
        } // while picking off tokens

        anext[i]=new int[fols.size()];
        for (int j=0;j<fols.size();j++) 
          anext[i][j]=((Integer)fols.elementAt(j)).intValue();
      } // if "next=<answer list>" specified
                               
      // Is "synonym" specified?  If so, fill in aap accordingly
      String synonym=aen[i].getAttribute("synonym");
      if (synonym==null) synonym="";
      if (!synonym.equals("")) {
        StringTokenizer st=new StringTokenizer(synonym,DELIMS);
        Vector<Integer> syns=new Vector<Integer>();
        while (st.hasMoreTokens()) {
          String ftok=st.nextToken();
          ftok=ftok.trim();
          if (!ftok.equals("")&&(DELIMS.indexOf(ftok)<0)) {
            Integer finteger=(Integer)ansmap.get(ftok);
            if (finteger==null) {
              System.out.println(now()+" Answer "+aname[i]+" has synonym="
                +ftok+" (undefined)");
              System.exit(1);
            }
            syns.addElement(finteger);
          }
        } // while picking off tokens

        syn[i]=new int[syns.size()];
        for (int j=0;j<syns.size();j++) 
          syn[i][j]=((Integer)syns.elementAt(j)).intValue();
      } // if "synonym=<answer list>" specified

      // Normally, the "essence" of a topic is the topic itself.
      // But if this topic <include>'s another, then the essence
      // is the include'd topic.  The essence is used to determine
      // if a topic is already "used"
      Node n=aen[i].getFirstChild();
      String essname=aname[i];
      while (n!=null) {
        if (n.getNodeName().equals("include")) {
          Element ien=(Element)n;
          essname=ien.getAttribute("answer");
          if (essname==null) essname="";
          essname=essname.trim();
          if (essname.equals("")) {
            System.out.println(now()+" <include> without answer in "+aname[i]);
            System.exit(1);
          }
          if (aname[i].toUpperCase().equals(essname.toUpperCase())) {
            System.out.println(now()+" Topic "+aname[i]+" <include>'s itself!");
            System.exit(1);
          }
        }                      
        n=n.getNextSibling();
      } // while looking for <include>
      String essattr=aen[i].getAttribute("essence");
      if (essattr==null) essattr="";
      essattr=essattr.trim();
      if (!essattr.equals("")) essname=essattr;
      int itop=-1;
      for (int j=0;j<acnt;j++)
        if (aname[j].toUpperCase().equals(essname.toUpperCase())) itop=j;
      if (itop<0) {
        System.out.println(now()+" Nonexistant topic \""
          +essname+"\" referenced in "+aname[i]);
        System.exit(1);
      }
      aess[i]=itop;
    } // for answer i

    // Closure on aess[i]
    int lastchange=0;
    int epass=0;
    while (lastchange>=0) {
      lastchange=-1;
      epass++;
      for (int i=0;i<acnt;i++)
        if (aess[aess[i]]!=aess[i]) {
          aess[i]=aess[aess[i]];
          lastchange=i;
          if (aess[i]==i) {
            System.out.println(now()+" Topic "+aname[i]+" <include>'s itself!");
            System.exit(1);
          }
        }
      if ((lastchange>=0)&&(epass>acnt)) {
        System.out.println(now()+" <include> loop involving "
          +aname[lastchange]);
        System.exit(1);
      }
    } // while essence still changing

    //for (int i=0;i<acnt;i++)
    //  if (aess[i]!=i)
    //    System.out.println(now()+" Essence of "+aname[i]+" is "+aname[aess[i]]);

    System.out.println(now()+" We have "+qcnt
      +" example questions containing "+wcnt+" distinct words.");
    System.out.println(now()+" There are "+ansmap.size()
      +" answers.");
                                            
    for (int i=0;i<qnw.length;i++) {
      String s=now();
      for (int j=0;j<qnw[i].length;j++)
        s+=" "+wstr[qnw[i][j]];
      s+=" -> "+aname[qa[i]];
      // System.out.println(s);
    } // for question i 
  
    // wok[i] is true if word i is a key word (not noise)
    // Initially, we assume all words not explicitly specified
    // as noise are key.
    wok=new boolean[wcnt];
    boolean[] wnoise=new boolean[wcnt];
    boolean[] wkey=new boolean[wcnt];

    for (int i=0;i<wcnt;i++) {
      wok[i]=true;
      wnoise[i]=false;
      wkey[i]=false;
    }

    if (noiv!=null) {
      for (int i=0;i<noiv.size();i++) {
        int j=noiv.elementAt(i).intValue();
        wnoise[j]=true;
        wok[j]=false;
      }
    } // set wnoise

    if (keyv!=null) {
      for (int i=0;i<keyv.size();i++) {
        int j=((Integer)keyv.elementAt(i)).intValue();
        wkey[j]=true;
      }               
    } // set wkey
    
    // "keysymcnt" is the number of key symbols (words & phrases) 
    keysymcnt=0;         
    for (int i=0;i<phrcnt;i++) if (phrkey[i]) keysymcnt++;
    for (int i=0;i<wcnt;i++)   if (wok[i])    keysymcnt++;


    // Compute wacnt[i][j]: times symbol i occurs in q's with answer j
    //         aqcnt[i]     # questions having answer i
    //         awcnt[j]     # valid symbol posits in q's for answer j
    //                      (assuming all words are in the model)
    //
    // In this, when I say "symbol" I am referring to words or phrases.
    // Symbol i is a word if it is 0..wcnt-1, and a phrase if it is
    // wcnt..wcnt+phrcnt-1.
    wacnt=new int[wcnt+phrcnt][acnt];
    awcnt=new int[acnt];
    aqcnt=new int[acnt];
    boolean dumbq=false;
    for (int i=0;i<qcnt;i++) {
      int ians=qa[i];
      if (ians>=0) {   
        aqcnt[ians]++;
        Vector<Integer> symv=getkeysyms(qnw[i]);
        if (symv.size()>0) {
          for (int j=0;j<symv.size();j++)
            wacnt[symv.elementAt(j).intValue()][ians]++;
          awcnt[ians]+=symv.size();
        }
        else {
          if (!dumbq) {
            dumbq=true;
            System.out.println(now()+" The following question(s) have no keywords or keyphrases.  They");
            System.out.println(now()+" can only be recognized through exact matches.  Consider idenifying");
            System.out.println(now()+" key words or phrases if the questions have variants.");
          }
          String s="  "+aname[ians];
          while (s.length()<12) s+=" ";
          s+=" ";
          for (int j=0;j<qnw[i].length;j++)
            s+=" "+wstr[qnw[i][j]];
          System.out.println(now()+s);
        } // no key words/phrases
      }
    }
                                             
                            
    // Now we go through each word in turn, determining if
    // each word actually helps the success rate.
    // Pass -1 is a kludge:  it measures the current 'wok' performance.
    int bestright=0;
    for (int mm=0;mm<4;mm++)
      for (int m=-1;m<wok.length;m++)
        if ((m<0)||!(wnoise[m]||wkey[m])) {
        if (m>=0) {                                    
          if (wok[m]) keysymcnt--;
          else keysymcnt++;
          wok[m]=!wok[m];
        }

        // Compute model accuracy for example questions
        // Score=number of correct classifications
        // 2 points for clear win, 1 point for a tie
        int right=0;
        for (int i=0;i<qcnt;i++)
          if (aact[qa[i]]) {
            // Retain best answer so far (best computed p)
            double bestp=-1e20;  
            boolean tie=false;
            double epsilon=0.001;
            double rightp=0.0;

            Vector<Integer> symv=getkeysyms(qnw[i]);

            // Consider each answer, seeking the best match
            for (int j=0;j<acnt;j++)
              if (aact[j]&&(aqcnt[j]>0)) {
                double logp=0.0;             
                for (int k=0;k<symv.size();k++) {
                  int key=symv.elementAt(k).intValue();
                  logp+=Math.log((wacnt[key][j]+1.0)/(keysymcnt+awcnt[j]));
                } // for keylist element k
                                                                       
                if (j==qa[i]) rightp=logp;
                if (logp>bestp-epsilon) tie=true;
          
                if (logp>bestp) {
                  tie=(bestp>logp-epsilon);
                  bestp=logp;
                }                 
              } // for answer j
    
            if (rightp>bestp-epsilon) {
              right++;
              if (!tie) right++;
            }
          } // for example question i

        if (m<0) {
          bestright=right;
          if (mm==0)
            System.out.println(now()+" Model accuracy (initial)         "
              +show((50.0*bestright)/qcnt,5,1)+"%");
        } // initial calibration pass
        else {
          if (bestright>right) {
            if (wok[m]) keysymcnt--;
            else keysymcnt++;
            wok[m]=!wok[m];
          }  
          else
            bestright=right; // leave out this word
        }
      } // for test of word m

                                             
    System.out.println(now()+" ");
    System.out.println(now()+" Model accuracy (selected words)  "
      +show((50.0*bestright)/qcnt,5,1)+"%");


    // Display keywords
    int keys=0;
    for (int i=0;i<wok.length;i++) if (wok[i]&&!wkey[i]) keys++;
    if (keys>0) {
      System.out.println(now()
        +" Teacher guessed the following "+keys+" words are keywords:");
      String ns="";
      for (int i=0;i<wok.length;i++)
        if (wok[i]&&!wkey[i]) {
          String w=wstr[i];
          if (ns.length()+w.length()>59) {
            System.out.println(now()+"  "+ns);
            ns="";
          }
          ns+=" "+w;
        }                   
      System.out.println(now()+"  "+ns);
    }

    // Display noise words
    int noise=0;
    for (int i=0;i<wok.length;i++) if (!wok[i]&&!wnoise[i]) noise++;
    if (noise>0) {
      System.out.println(now()
        +" Teacher guessed the following "+noise+" words are noisewords:");
      String ns="";
      for (int i=0;i<wok.length;i++)
        if (!wok[i]&&!wnoise[i]) {
          String w=wstr[i];
          if (ns.length()+w.length()>59) {
            System.out.println(now()+"  "+ns);
            ns="";
          }
          ns+=" "+w;
        }                   
      System.out.println(now()+"  "+ns);
    }


    // Display sentences which give us problems
    boolean showedmisclass=false;
    for (int i=0;i<qcnt;i++)
      if (aact[qa[i]]) {
        // Best classif, "right" classif
        double bestp=-1e20;
        int bestj=-1;
        double rightp=0.0;
        double sump=0.0;

        // Parse the sentence
        Vector<Integer> symv=getkeysyms(qnw[i]);

        // Consider each answer
        for (int j=0;j<acnt;j++)
          if (aact[j]&&(aqcnt[j]>0)) {
            double logp=0.0;       
            for (int k=0;k<symv.size();k++) {
              int key=symv.elementAt(k).intValue();
              logp+=Math.log((wacnt[key][j]+1.0)/(keysymcnt+awcnt[j]));
            } // for keylist element k

            double p=0.0;
            if (logp>-40.0) p=Math.exp(logp);
                                              
            if (j==qa[i]) rightp=p;
                          
            if (p>bestp) {
              bestj=j;
              bestp=p;      
            }

            sump+=p;
          } // for answer j

        if (bestp>rightp) {
          if (!showedmisclass) {
            System.out.println(now()+" These questions are hard to classify.  Consider tweaking keywords,");
            System.out.println(now()+" noisewords, key phrases, or noise phrases for these.  Or, you may");
            System.out.println(now()+" even find you want to switch the example question's classification.");
            showedmisclass=true;
          }

          String qstr="";
          for (int j=0;j<qnw[i].length;j++)
            qstr+=" "+wstr[qnw[i][j]];

          System.out.println(now()+" Question is: "+qstr);
          System.out.println(now()
            +"   Example:  "+aname[qa[i]]
            +" ("+show(rightp*100.0/sump,3)+"%)"
            +"   Best Fit:  "+aname[bestj]
            +" ("+show(bestp*100.0/sump,3)+"%)");
          System.out.println(now()+"   Key symbols:  "+showkeysyms(symv));
        } // display misclassification  
      } // for example question i



    // kludge
    //try {
    //  FileWriter knw=new FileWriter("knw.txt");
    //  for (int i=0;i<qcnt;i++) {
    //    String kstr=aname[qa[i]];
    //    while (kstr.length()<10) kstr+=" ";
    //    kstr+="  ";
    //    for (int j=0;j<qnw[i].length;j++)
    //      if (wok[qnw[i][j]])
    //        kstr+=" "+wstr[qnw[i][j]].toUpperCase();
    //      else
    //        kstr+=" "+wstr[qnw[i][j]].toLowerCase();
    //    knw.write(kstr+"\n");
    //  }
    //  knw.close();
    //} catch (Exception e) {
    //  System.out.println(now()+" Error writing knw:  "+e);
    //}
  }  // chatengine constructor  

        
  int[] parse(String s) {

    StringTokenizer st=new StringTokenizer(s,DELIMS);
    Vector<Integer> wordnums=new Vector<Integer>(); // word number
                                   // sequence for this
    while (st.hasMoreTokens()) {
      String ss=st.nextToken().trim().toUpperCase();
      if ((ss.length()>0)&&(DELIMS.indexOf(ss)<0)) {
        if (ss.length()>=7) ss=ss.substring(0,6)+"*";

        // Remove 's 't 'm 've 'll sorts of things
        int qpos=ss.indexOf("'");
        if ((qpos+3>=ss.length())&&(qpos>0))
          ss=ss.substring(0,qpos);

        Integer wno=(Integer)mapw.get(ss);
        if (wno!=null) wordnums.addElement(wno);
      }          
    } // while more tokens to look at

    int[] result=new int[wordnums.size()];
    for (int i=0;i<result.length;i++)
      result[i]=((Integer)wordnums.elementAt(i)).intValue();

    return result;
  } // parse


  //
  // This method "parses" a series of words, generating a list of
  // key words & key phrases which appear in the sentence.
  //
  // Parsing is pretty simple.  We look for keyword, key phrase,
  // noiseword, and noise phrase matching.  Each input word is associated
  // with one and only one match... maybe key, maybe noise.  Longer matches
  // have precedence, and if it's still a "tie", key matches are preferred
  // over noise.
  //
  // Once parse is settled, we use the resulting key list
  // (phrases, words) to compute probability.
  //                                          
  // Note that we don't iterate or tranform the sentence.
  //
  Vector<Integer> getkeysyms(int[] sentence) {
    // Phrases have been sorted in descending precedence order.
    // Therefore, we can look for matches of each phrase in
    // sequence, and when we get a match mark those words "taken".
    boolean[] taken=new boolean[sentence.length]; 
    for (int i=0;i<sentence.length;i++) taken[i]=false;
                                                        
    // "symv" is a list of symbols in this sentence. 
    Vector<Integer> symv=new Vector<Integer>();
    for (int i=0;i<phrcnt;i++)
      for (int j=0;j<sentence.length-phrlen[i]+1;j++) {
        boolean match=true;
        for (int k=0;match&&(k<phr[i].length);k++) {
          if (sentence[j+k]!=phr[i][k]) match=false;
          if (taken[j+k]) match=false;
        }
        if (match) {
          if (phrkey[i]) symv.addElement(new Integer(wcnt+i));
          for (int k=0;k<phr[i].length;k++) taken[j+k]=true;
        }
      } // seek phrase i at sentence position j

    for (int i=0;i<sentence.length;i++)
      if (!taken[i]&&wok[sentence[i]])
        symv.addElement(new Integer(sentence[i]));

    return symv;
  } // getkeysyms

  String showkeysyms(Vector<Integer> symv) {
    String s="";

    // Show the parse
    if (symv.size()==0)
      s="No key symbols.";
    else {
      for (int i=0;i<symv.size();i++) {   
        int k=symv.elementAt(i).intValue();
        if (k<wcnt) s+=wstr[k];
        else {
          k-=wcnt;
          for (int j=0;j<phr[k].length;j++) {
            s+=wstr[phr[k][j]];
            if (j+1<phr[k].length)s+=" ";
          }
        }
        if (i+1<symv.size())s+=",";
      }
    }                        

    return s;
  } // showkeysyms


  //
  // Classify a sentence, yielding answer probabilities.
  //
  // q2a maps question topics to answer topics.
  // if q2a[i] is <0, then question i is not poss in this context.
  // otherwise q2a[i] is answer topic.  Note that many question topics
  // may map into one answer.
  //
  double[] classify(int[] sentence,int [] q2a) {
    // You guessed it, this is the resulting probability vector
    double[] cprob=new double[acnt];

    // Exact match of sentence with example question always "win"
    int xact=-1;
    for (int i=0;(i<qcnt)&&(xact<0);i++)
      if ((q2a[qa[i]]>=0)&&(qnw[i].length==sentence.length)) {
        boolean match=true;
        for (int j=0;match&&(j<sentence.length);j++)
          if (qnw[i][j]!=sentence[j]) match=false;
        if (match) xact=i;
      }

    if (xact>=0) { // exact match of question
      for (int i=0;i<acnt;i++) cprob[i]=0.0;
      cprob[q2a[qa[xact]]]=1.0;
    }
    else { // no exact match
      // Get key symbols in sentence
      Vector symv=getkeysyms(sentence);

      // Which answers are viable?
      boolean[] viab=new boolean[acnt];
      for (int i=0;i<acnt;i++) viab[i]=false;
      for (int i=0;i<acnt;i++)
        if ((aqcnt[i]>0)&&(q2a[i]>=0))
          viab[q2a[i]]=true;
           
      // Compute probability for each answer
      for (int i=0;i<acnt;i++)
        if (!viab[i]) cprob[i]=-1e20;
        else {
          cprob[i]=0.0;
          for (int j=0;j<symv.size();j++) {
            int key=((Integer)symv.elementAt(j)).intValue();
            double top=0.0;
            double bot=0.0;
            for (int k=0;k<acnt;k++)
              if (q2a[k]==i) {
                top+=wacnt[key][k];
                bot+=awcnt[k];
              }
            cprob[i]+=Math.log((top+1.0)/(keysymcnt+bot));
          } // for keylist element j
        } // consider viable answer
                                      
      // Now cprob is log p for each answer.  Be careful
      // to avoid underflo
      double maxprob=-1e20;
      for (int i=0;i<acnt;i++)
        if (cprob[i]>maxprob) maxprob=cprob[i];
    
      double sump=0.0;
      for (int i=0;i<acnt;i++) {
        if (cprob[i]+10.0<maxprob)
          cprob[i]=0.0;
        else
          cprob[i]=Math.exp(cprob[i]-maxprob);
        sump+=cprob[i];
      } // for each log p value

      for (int i=0;i<acnt;i++) cprob[i]/=sump;
    } // no exact match

    return cprob;
  } // classify

  String now() {
    return DateFormat
           .getTimeInstance(DateFormat.MEDIUM)
           .format(new java.util.Date());
  } // now
                     
  public String show(double d) {
    double z=Math.abs(d);
    if (z>=10000.0) return show(d,0);
    else if (z>=1000.0) return show(d,1);
    else if (z>=100.0) return show(d,2);
    else if (z>=10.0) return show(d,3);
    else if (z>=1.0) return show(d,4);
    else if (z>=0.1) return show(d,5);
    else if (z>=0.01) return show(d,6);
    else if (z>=0.001) return show(d,7);
    else return show(d,8);
  } // show

  public String show(double dd, int i) {
    double d=dd;

    if ((i==0)&&(Math.abs(dd)<0.5)) d=0.0;
    if ((i==1)&&(Math.abs(dd)<0.05)) d=0.0;
    if ((i==2)&&(Math.abs(dd)<0.005)) d=0.0;
    if ((i==3)&&(Math.abs(dd)<0.0005)) d=0.0;
    if ((i==4)&&(Math.abs(dd)<0.00005)) d=0.0;
    if ((i==5)&&(Math.abs(dd)<0.000005)) d=0.0;
    if ((i==6)&&(Math.abs(dd)<0.0000005)) d=0.0;
    if ((i==7)&&(Math.abs(dd)<0.00000005)) d=0.0;

    DecimalFormat nf=new DecimalFormat();

    DecimalFormatSymbols dfs=new DecimalFormatSymbols();
    dfs.setDecimalSeparator('.');
    nf.setDecimalFormatSymbols(dfs);

    nf.setGroupingUsed(false);
    if (i==0) { // integer display
      nf.setDecimalSeparatorAlwaysShown(false);
      nf.setMaximumFractionDigits(0);
    } else {
      nf.setDecimalSeparatorAlwaysShown(true);
      nf.setMinimumFractionDigits(1);
      nf.setMaximumFractionDigits(i);
      nf.setMinimumFractionDigits(i);
    } // decimal
    
    return nf.format(d);
  } // show with i decimal places

  public String show(double d, int i, int j) {
    String s=show(d,j);
    if (s.length()>=i) return s;
    while (s.length()<i) s=" "+s;
    return s;
  } // show in i-char field with j dec places

} // chatengine
 
