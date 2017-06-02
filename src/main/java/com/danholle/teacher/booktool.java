package com.danholle.teacher;

// 
// Tool for manipulating the teacher's "book"
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
import java.io.*;
import java.awt.image.*;
//import com.sun.image.codec.jpeg.*;
import javax.swing.*;
import java.lang.reflect.*;

public class booktool {
  
  // Stuff associated with The Book
  static Document doc;   // XML
  static Element root;

  // For newbook generation
  static FileWriter nb;
  static String nbbuf;
                 
  public static void main(String[] args) {
    Properties def= new Properties();
    def.setProperty("book",     "book.xml");
    def.setProperty("dir",      "example\\");
    def.setProperty("train",    ".");
    def.setProperty("qlist",    ".");
    def.setProperty("alist",    ".");
    def.setProperty("newbook",  ".");

    boolean okay=true;
    for  (int i=0;okay&&(i<args.length);i++) {
      String arg=args[i].trim();
      if (arg.equals("?")||arg.equals("/?")||arg.equals("-?")
        ||arg.equals("help")||arg.equals("/h")||arg.equals("-h"))
        okay=false;
      else {
        int eq=args[i].indexOf("=");

        if (eq<=0) {
          System.out.println(" Illegal key=value parameter: "+args[i]);
          okay=false;
        }
        else {
          String key=args[i].substring(0,eq);
          String val=args[i].substring(eq+1,args[i].length());
    
          if (def.getProperty(key)==null) {
            System.out.println(" Illegal key "+key+": "+args[i]);
            okay=false;
          }
          else def.setProperty(key,val);
        } // valid key=value
      } // not a plea for help
    } // for each key=val arg

 
    // Did parms look cool?  If not, lecture.
    if (!okay) {
      System.out.println(" ");
      System.out.println(" This is a simple maintenence tool for tool for the teacher's Book.  Usage:");
      System.out.println(" ");
      System.out.println("   java booktool [key=value] [key=value] ...");
      System.out.println(" ");
      System.out.println(" dir     defaults to example\\ and it is the directory for The Book");
      System.out.println("         to be used as input.  (An input book is required.) ");
      System.out.println(" book    defaults to book.xml and is the file name of The Book");
      System.out.println("         to be used as input. ");
      System.out.println(" alist   defaults to \".\" and is the file name of the answer summary");
      System.out.println("         file, giving a shortened version of the answer, along with next");
      System.out.println("         and expand info for the answer.  \".\" turns off file generation.");
      System.out.println(" qlist   defaults to \".\" and is the file name of the question summary");
      System.out.println("         file, giving a shortened version of the questions, sorted by answer.");
      System.out.println(" train   defaults to \".\" and is the file name of the question training file");
      System.out.println("         intended as input to voice recognition programs.  One question/line.");
      System.out.println(" newbook defaults to \".\" and is the file name of the new formatted copy ");
      System.out.println("         of The Book.");
      System.out.println(" ");
      System.exit(1);
    }
             
    String book=def.getProperty("book");
    String dir=def.getProperty("dir");
    String qlist=def.getProperty("qlist");
    String alist=def.getProperty("alist");
    String train=def.getProperty("train");
    String newbook=def.getProperty("newbook");

    try { // Try reading xml
      // If XML exists, pick it up.
      // If not, create empty XML document
      File f=new File(dir+book);
      if (f.exists()) {
        DocumentBuilderFactory xmlfactory=
          DocumentBuilderFactory.newInstance();
        DocumentBuilder xmlbuilder=xmlfactory.newDocumentBuilder();
        doc=xmlbuilder.parse(f);
        root=(Element)doc.getDocumentElement();
      } // read the XML
      else {
        System.out.println("XML not found.");
        System.exit(1);
      }
    } // try reading XML
    catch (Exception e) {
      System.out.println(" Error reading XML: "+e);
      System.exit(1);
    } // catch


    // 
    // Generate Question List
    //
    if (!qlist.equals("."))
      try {
        Vector<String> av=new Vector<String>();
        Vector<String> qv=new Vector<String>();

        Node n=root.getFirstChild();
        int anslen=6;
        while (n!=null) {
          if (n.getNodeName().equals("question")) {
            Element qen=(Element)n;

            String ansname=qen.getAttribute("topic");
            if (ansname==null) ansname="";
            ansname=ansname.trim();
            if (ansname.length()>anslen) anslen=ansname.length();
            av.addElement(ansname);

            String s="";
            Node nn=qen.getFirstChild();
            while (nn!=null) {
              if (nn.getNodeName().equals("#text")) 
                s+=nn.getNodeValue();
              nn=nn.getNextSibling();
            } // while puting together question text
            qv.addElement(s);
          } // handle question
          n=n.getNextSibling();
        } // loop looking for questions

        int qcnt=av.size();
        int[] rank=new int[qcnt];
        for (int i=0;i<qcnt;i++) rank[i]=i;
        for (int i=0;i<qcnt-1;i++)
          for (int j=i+1;j<qcnt;j++) 
            if (((String)av.elementAt(rank[i])).compareTo
               ((String)av.elementAt(rank[j]))>0) {
              int ij=rank[i];
              rank[i]=rank[j];
              rank[j]=ij;
            } // swap
              
        FileWriter qf=new FileWriter(qlist);
        qf.write("Questions from "+dir+book+" sorted by answer name\n \n");
        
        String empty=" ";
        for (int i=0;i<anslen;i++) empty+=" ";
        qf.write(("Answer  "+empty).substring(0,anslen)+"  Question\n");
        qf.write(("======  "+empty).substring(0,anslen)+"  ========\n");

        String lastname="";
        for (int i=0;i<qcnt;i++) {
          String buf=(((String)av.elementAt(rank[i]))+empty)
            .substring(0,anslen);
          if (!buf.equals(lastname)) {qf.write("\n");lastname=buf;}
          buf+="  "+((String)qv.elementAt(rank[i]));
          if (buf.length()>70) {
            buf=buf.substring(0,67);
            int iblk=buf.lastIndexOf(" ");
            if (iblk>0) buf=buf.substring(0,iblk);
            buf+="...";
          }
          qf.write(buf+"\n");
        }
        qf.close(); 
      } // try
      catch (Exception e) {
        System.out.println("Error writing question file:  "+e);
        e.printStackTrace();
        System.exit(1);
      }
    // End of Question List generation



    // 
    // Generate Answer List
    //
    if (!alist.equals("."))
      try {
        Vector<String> namev=new Vector<String>();
        Vector<String> nextv=new Vector<String>();
        Vector<String> expv=new Vector<String>();
        Vector<String> abegv=new Vector<String>();
        Vector<String> aendv=new Vector<String>();
           
        Node n=root.getFirstChild();
        int anslen=6;
        while (n!=null) {
          if (n.getNodeName().equals("answer")) {
            Element aen=(Element)n;

            // Answer name
            String ansname=aen.getAttribute("topic");
            if (ansname==null) ansname="";
            ansname=ansname.trim();
            if (ansname.length()>anslen) anslen=ansname.length();

            // Next list
            String nextlist=aen.getAttribute("next");
            if (nextlist==null) nextlist="";
            nextlist=nextlist.trim();

            String explist="";
                                       
            // Take a pass over answer, collecting text
            // Also make note of explicit next, expand
            String text="";  // answer text
            Node nn=aen.getFirstChild();
            while (nn!=null) {
              String chunk="";
              if (nn.getNodeName().equals("#text")) 
                chunk=nn.getNodeValue();
              else chunk=eltext(nn);               

              chunk=chunk.trim();
              if (chunk.length()>0) text+=" "+chunk;

              nn=nn.getNextSibling();
            } // while puting together question text

            String abeg=text.trim();
            String aend="";
            int linlen=52;
            if (abeg.length()>5+linlen) {
              int ibeg=(abeg.substring(0,linlen)).lastIndexOf(" ");
              if (ibeg<0) ibeg=linlen;

              aend=abeg.substring(ibeg,abeg.length()).trim();
              abeg=abeg.substring(0,ibeg);

              if (aend.length()>5+linlen) {
                abeg+="...";
                aend=aend.substring(aend.length()-linlen,aend.length());
                ibeg=aend.indexOf(" ");
                if (ibeg>0) aend=aend.substring(ibeg,aend.length()).trim();
                aend="..."+aend;
              }

            }   
            namev.addElement(ansname);
            nextv.addElement(nextlist);
            expv.addElement(explist);
            abegv.addElement(abeg);
            aendv.addElement(aend);
          } // handle               
          n=n.getNextSibling();
        } // loop looking for questions

        int acnt=namev.size();
        int[] rank=new int[acnt];
        for (int i=0;i<acnt;i++) rank[i]=i;
        for (int i=0;i<acnt-1;i++)
          for (int j=i+1;j<acnt;j++) 
            if (((String)namev.elementAt(rank[i])).compareTo
               ((String)namev.elementAt(rank[j]))>0) {
              int ij=rank[i];
              rank[i]=rank[j];
              rank[j]=ij;
            } // swap
              
        FileWriter af=new FileWriter(alist);
        af.write("Answers from "+dir+book+" sorted by answer name\n \n");
        
        String empty="   ";
        for (int i=0;i<anslen;i++) empty+=" ";
        af.write(("Answer  "+empty).substring(0,anslen)+"  Answer Info\n");
        af.write(("======  "+empty).substring(0,anslen)+"  ===========\n");

        for (int i=0;i<acnt;i++) {
          String buf=(((String)namev.elementAt(rank[i]))+empty)
            .substring(0,anslen+2)+((String)abegv.elementAt(rank[i]));
          af.write("\n"+buf+"\n");                     

          buf=(String)aendv.elementAt(rank[i]);
          if (buf.length()>0)
            af.write(empty.substring(0,anslen+2)
              +buf+"\n");

          buf=(String)nextv.elementAt(rank[i]);
          if (buf.length()>0)
            af.write(empty.substring(0,anslen+2)
              +"NEXT:    "+buf+"\n");

        }                                           
        af.close(); 
      } // try
      catch (Exception e) {
        System.out.println("Error writing answer file:  "+e);
        e.printStackTrace();
        System.exit(1);
      }
    // End of Question List generation



    if (!newbook.equals(".")) {
      try {nb=new FileWriter(newbook);}
      catch (Exception e) {
        System.out.println("Error opening new book: "+e);
        e.printStackTrace();
      }  
      nbbuf="";
      nbwrite(0,root);
      nbflush();
      try {nb.close();}
      catch (Exception e) {
        System.out.println("Error closing new book: "+e);
        e.printStackTrace();
      }  
    } // newbook
 
    System.exit(0);
  } // main

  static void nbput(int indent,String s) {
    if (nbbuf.length()+s.length()>70) nbflush();
    if (nbbuf.length()>0) nbbuf+=s;
    else {
      while (nbbuf.length()<indent) nbbuf+=" ";
      if (s.trim().length()>0) nbbuf+=s;
    }
  } // nbput

  static void nbflush() {
    if (nbbuf.trim().length()>0) nbwrite(nbbuf+"\n");
    nbbuf="";
  } // nbflush

  static void nbwrite(String s) {
    try {                      
      nb.write(s);
    } // try
    catch (Exception e) {
      System.out.println("Error writing new book: "+e);
      e.printStackTrace();
    }  
  } // nbflush

  static void nbsort(int indent, Node nn) {
    Node n=nn;
    Vector<String> v=new Vector<String>();
    while (n!=null) {               
      String tag=n.getNodeName();
      if (tag.equals("#text")) {
        String s=n.getNodeValue();
        if (s==null) s="";
        s=s.trim();
        if (!s.equals("")) {
          int ip=0;
          while((ip=s.indexOf(" "))>0) {
            v.addElement(s.substring(0,ip));
            s=s.substring(ip,s.length()).trim();
          }
          v.addElement(s);
        }
      } // handle text
      else {
        System.out.println("Node "+n.getParentNode().getNodeName()
          +" contains "+tag+", not text");
        System.exit(1);
      }
      n=n.getNextSibling();
    }
    if (v.size()>0) {
      int[] srt=new int[v.size()];
      for (int i=0;i<srt.length;i++) srt[i]=i;
      for (int i=0;i<srt.length-1;i++)
        for (int j=i+1;j<srt.length;j++) {
          String left=((String)v.elementAt(srt[i])).toUpperCase();
          String right=((String)v.elementAt(srt[j])).toUpperCase();
          if (left.compareTo(right)>0) {
            int temp=srt[i];
            srt[i]=srt[j];
            srt[j]=temp;
          }
        }
 
      String sind="";
      while (sind.length()<indent)sind+=" ";
      String buf="";
      for (int i=0;i<srt.length;i++) {
        String word=(String)v.elementAt(srt[i]);
        if (indent+buf.length()+word.length()+1<70)
          buf+=word+" ";
        else {
          nbwrite(sind+buf+"\n");
          buf=word+" ";
        }
      }
      nbwrite(sind+buf+"\n");

    } // at least one word
    
  } // nbsort

  static void nbwrite(int indent, Node n) {
    if (n!=null) {
      String tag=n.getNodeName();

      if (!tag.equals("question")) {

      // Flush (left align) before element if tag suggests that
      if (tag.equals("question")
      ||tag.equals("answer")
      ||tag.equals("book")
      ||tag.equals("p")
      ||tag.equals("ul")
      ||tag.equals("li")
      ||tag.equals("random")
      ||tag.equals("choice")
      ||tag.equals("btw")
      ||tag.equals("img")
      ||tag.equals("noise")
      ||tag.equals("key")
      ||tag.equals("else")
      ||tag.equals("comment")
      ||tag.equals("table")
      ||tag.equals("tr")
      ||tag.equals("td")
      ||tag.equals("noisewords")
      ||tag.equals("keywords")
      ||tag.equals("hlink")
      ||tag.equals("leadtopics")
      ||tag.equals("a"))
        nbflush();

      if (tag.equals("#text")) {
        String ss=n.getNodeValue();
        while (!ss.equals("")) {
          int bpos=ss.indexOf(" "); 
          if (bpos==0) {
            nbput(indent," ");
            while (ss.indexOf(" ")==0) ss=ss.substring(1,ss.length());
          }
          else {
            if (bpos>0) {
              nbput(indent,ss.substring(0,bpos));
              ss=ss.substring(bpos,ss.length());
            }
            else {
              nbput(indent,ss);
              ss="";
            }
          }
        } // while stuff remains to be put ut
      } // handle #text       
      else {
        // Prior to any answer, put out a blank line followed
        // by all the questions associated with that answer.
        // THEN do the answer.
        if (tag.equals("answer")) {
          Element aen=(Element)n;
          String aname=aen.getAttribute("topic");
          if (aname==null) aname="";
          aname=aname.toUpperCase();

          nbwrite(" \n");

          Node qn=root.getFirstChild();
          while (qn!=null) {
            if (qn.getNodeName().equals("question")) {
              Element qen=(Element)qn;
              String qname=qen.getAttribute("topic");
              if (qname==null) qname="";
              if (aname.equals(qname.toUpperCase())) {
                String qind="";
                while (qind.length()<indent) qind+=" ";
                String qfirst="<question topic=\""+qname+"\">";
                String qlast="</question>";
                Node qtn=qen.getFirstChild();
                while ((qtn!=null)&&!qtn.getNodeName().equals("#text"))
                  qtn=qtn.getNextSibling();
                String qtext=qtn.getNodeValue().trim();

                if (qind.length()+qfirst.length()
                +qtext.length()+qlast.length()>70) {
                  nbwrite(qind+qfirst+"\n");
                  nbwrite(qind+"  "+qtext+"\n");
                  nbwrite(qind+qlast+"\n");
                }
                else nbwrite(qind+qfirst+qtext+qlast+"\n");
              } // display question                          

            } // process question

            qn=qn.getNextSibling();
          } // loop seeking questions

        } // special case treatment for answer
        

        nbput(indent,"<"+tag);
        
        Element en=(Element)n;
        NamedNodeMap nnm=en.getAttributes();
        for (int i=0;i<nnm.getLength();i++) {
          Node an=nnm.item(i);
          String attr=an.getNodeName();
          String value=an.getNodeValue();

          nbput(indent," ");
          nbput(indent,attr+"=\""+value+"\"");
        }

        Node cn=en.getFirstChild();
        if (cn!=null) {
          nbput(indent,">");
          if (tag.equals("answer")
          ||tag.equals("random")
          ||tag.equals("ul")
          ||tag.equals("choice")
          ||tag.equals("comment")
          ||tag.equals("table")
          ||tag.equals("td")
          ||tag.equals("tr")
          ||tag.equals("btw")
          ||tag.equals("else")
          ||tag.equals("noisewords")
          ||tag.equals("keywords")
          ||tag.equals("leadtopics")     
          ||tag.equals("book")) {
            nbflush();
            if (tag.equals("noisewords")||tag.equals("keywords"))
              nbsort(indent+2,cn);
            else {
              nbwrite(indent+2,cn);
              nbflush();
            }
            nbput(indent,"</"+tag+">");
            nbflush();
          }
          else {
            nbwrite(indent,cn);
            nbput(indent,"</"+tag+">");
          } 
        }
        else
          nbput(indent,"/>");
      } // element node
           
      // Flush (left align) after element if tag suggests that
      if (tag.equals("question")
      ||tag.equals("answer")
      ||tag.equals("book")
      ||tag.equals("random")
      ||tag.equals("p")
      ||tag.equals("ul")
      ||tag.equals("li")
      ||tag.equals("choice")
      ||tag.equals("btw")
      ||tag.equals("img")
      ||tag.equals("noise")
      ||tag.equals("key")
      ||tag.equals("else")
      ||tag.equals("noisewords")
      ||tag.equals("keywords")
      ||tag.equals("hlink")
      ||tag.equals("comment")
      ||tag.equals("table")
      ||tag.equals("td")
      ||tag.equals("tr")
      ||tag.equals("leadtopics")
      ||tag.equals("a"))
        nbflush();
      } // not a question

      // Carry on down the line
      nbwrite(indent,n.getNextSibling());
    } // non null node
  } // nbwrite       

  static String eltext(Node n) {
    String s="";
    Node nn=n.getFirstChild();
    while (nn!=null) {
      if (nn.getNodeName().equals("#text")) {
        String ss=nn.getNodeValue();
        if (ss==null) ss="";
        ss=ss.trim();
        if (!ss.equals("")) {
          if (s.equals("")) s=ss;
          else s+=" "+ss;
        }
      } // handle text
      nn=nn.getNextSibling();
    } // while handling element's kids

    return s;
  } // 
                          
} // booktool
