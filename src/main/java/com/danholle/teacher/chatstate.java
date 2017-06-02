package com.danholle.teacher;

// Remove nextans from the seq string
// Scan all stuff for references to <expand, <next

import java.lang.*;
import java.lang.Double;
import java.util.*;
import java.text.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.apache.xalan.serialize.*;
import org.apache.xalan.templates.*;
import javax.xml.transform.*;
import java.io.*;
 
class chatstate {
  chatengine c; // knowledge base
  boolean newc;
  String hostname;

  // Inbound info
  Vector<Integer> aseq;  // answer sequence; each elt is an Integer(ans#)
  String ques;        // Inbound question text.
  int nextans;        // ans# for default next answer

  // Outbound stuff, computed from the above
  boolean[] aused;    // true if answer has been used
  int context;        // last answer# to set context
  boolean last;       // True if this is our last response
  double[] pnext;     // Probs of next guys
  int[] nextcand;     // Likely next answers, most likely first
  String wans;        // Generated answer


  chatstate(chatengine chatter,String h) {
    c=chatter;
    aseq=null;
    ques=null;
    hostname=h;
    newc=false;
  } // constructor

  void setques(String q) {
    ques=q;
  } // setq

  void setseq(String seq) {
    //System.out.println("setseq with "+seq);
    aused=new boolean[c.acnt];
    for (int i=0;i<c.acnt;i++) aused[i]=false;
    context=-1;
    nextans=-1;

    aseq=new Vector<Integer>();

    StringTokenizer st=new StringTokenizer(seq,"x");
    String scr="";
    while (st.hasMoreTokens()) {
      String tok=st.nextToken();
      try {
        int i=Integer.parseInt(tok);
        if (nextans<0) nextans=i; // first ans is default next
        else {
          aseq.addElement(new Integer(i));
          scr+=" "+c.aname[i];
          aused[i]=true;
          aused[c.aess[i]]=true;
          context=i;
        } 
      } catch (Exception e) {;}
    } // while tokens left
    //System.out.println("setseq: nextans="+nextans+" and seq size="+aseq.size());
    //System.out.println("Answers so far: "+scr);
  } // setseq

  void respond() {
    String s=ques;
    System.out.println(now()+" Question: "+s);

    pnext=flow();
    for (int i=0;i<c.acnt;i++) {
      if (aused[c.aess[i]]&&!c.arpt[i]) pnext[i]=0.0;
      if (!c.aofr[i]) pnext[i]=0.0;
    }
    findnextcand();
    nextans=nextcand[0];             
     
     
    // Compute probs for all answers.
    // Initially, all answers created equal.
                          
    // "pcont" is the conditional probability based on context
    double[] pcont=flow();
     
    // "pclass" is the conditional probability based on sentence classif.
    double[] pclass=null;
                          
    boolean forced=false;
    if ((s.indexOf("$$")==0)||s.trim().equals("")) {
      forced=true;

      for (int i=0;i<c.acnt;i++) pcont[i]=1.0/c.acnt;

      String key=s.trim();
      if (key.length()==0) key="$$next";

      key=key.substring(2,key.length());
      String ukey=key.toUpperCase();

      int wno=-1;
      for (int i=0;i<c.acnt;i++)
        if (c.aname[i].toUpperCase().equals(ukey))
          wno=i;

      pclass=new double[c.acnt];
      if (wno<0)
        System.out.println(now()+" chatengine:  answer "+key+" not found");
      else 
        for (int i=0;i<c.acnt;i++)
          if (i==wno) pclass[i]=1.0;
          else pclass[i]=0.0;
    } // direct answer selection
    else { // Sentence
      int[] sentence=c.parse(s);

      String sentstr=now()+" Parsed:";
      for (int j=0;j<sentence.length;j++)
        sentstr+=" "+c.wstr[sentence[j]];
      System.out.println(sentstr);
        
      // Array q2a maps a question classification to the indicated
      // answer classification.  -1 means the question classification
      // is not active, e.g. "why" in a context when why has no meaning.
      int[] q2a=new int[c.acnt];
      for (int i=0;i<c.acnt;i++) q2a[i]=-1;
                                   
      // Apply synonym redirection.
      // We go through lead topics in ascending priority sequence
      // Then we go through "next=" topics in ascending sequence.
      int nmax=0;
      if ((context>=0)&&(c.anext[context]!=null))
        nmax=c.anext[context].length;

      for (int ii=0;ii<nmax+c.ltops.length;ii++) {
        // Get next answer#
        int i=c.ltops.length-ii-1;
        if (i<0) i=c.anext[context][nmax+i];
        else i=c.ltops[i];

        q2a[i]=i;
        if (c.syn[i]!=null)
          for (int j=0;j<c.syn[i].length;j++)
            q2a[c.syn[i][j]]=i;
      }                        

      boolean redir=false;
      for (int i=0;i<c.acnt;i++)
        if ((q2a[i]>=0)&&(q2a[i]!=i)) {
          if (!redir)
            System.out.println(now()+" Q to A Redirects:");
          redir=true;
          System.out.println(now()+"  "+c.aname[i]
            +" => "+c.aname[q2a[i]]);
        }
      if (!redir) System.out.println(now()+" No Q to A Redirects.");

      System.out.println(now()+" Key symbols: "
        +c.showkeysyms(c.getkeysyms(sentence)));
      pclass=c.classify(sentence,q2a);

    } // Sentence                


    // 
    // Answer Generation:  The Basics
    //
    // Any answer with a nonzero pclass and nonzero pcont is a
    // viable candidate.  We use pclass to determine the
    // user's intent.
    //
    // We can generate several kinds of answers at this stage.
    //  1.  Don't understand.  No candidate has prob>thresh.
    //      Use the "duh" answer definition and say something
    //      like "I don't follow your question... The words
    //      x and y are not in my vocabulary."  Follow on topics
    //      are those you'd get from the previous context.
    //  2.  Ambiguous.  There is an answer with probability
    //      p which is greater than thresh, but there are
    //      other answers with probability > p*ambig.
    //      We use the "ambig" answer definition and say
    //      something like "Not sure... are you asking
    //      about x or y or ...?  Pick one, or change your
    //      question."  Follow on topics reflect the probs
    //      we got for this one (question*context)
    //  3.  Rehash.  We selected an answer that's already been
    //      given, fair and square.  Use the "rehash" answer
    //      and say something like "We've already talked about
    //      x... want to hear about it again?" Follow on
    //      topics are the q*c ones leading to this choice
    //  4.  Normal answer.  That is, the classifier and the
    //      next-topic probabilities combine to come up with
    //      one conclusion;  and we present it.  Follow on
    //      topics can be determined using this answer for
    //      context.
    //


    pnext=new double[c.acnt];
    double sump=0.0;
    int maxi=0;
    int pcnt=0; // Number of viable answers
    double maxclass=0.0; // pclass of "best" answer
    for (int i=0;i<c.acnt;i++) {                    
      pnext[i]=0.0;
      if ((pclass[i]>0.0)&&(pcont[i]>0.0)) {
        pcnt++;
        sump+=pclass[i];
        if (pclass[i]>maxclass) {
          maxi=i;
          maxclass=pclass[i];
        }
      }
    }

    // Now pcnt is the number of viable answers (pclass, pcont both >0).
    if (pcnt==1)
      System.out.println(now()+" One viable answer.");
    else
      System.out.println(now()+" "+pcnt+" viable answers.");

    // Get tcnt, the number of viable answers meeting threshold.
    // Get scnt, the number of viable answers within ratio ambig of best
    //   which are "available" (i.e. repeatable or not used)
    int tcnt=0;
    int scnt=0;
    int besta=-1;
    System.out.println(now()+" pclass thresh is "
      +show(100.0*c.thresh*sump/pcnt,2)+"%");
    for (int i=0;i<c.acnt;i++)
      if ((pclass[i]>0.0)
      &&(pcont[i]>0.0)
      &&((pcnt==1)||(pclass[i]>=c.thresh*sump/pcnt))) {
        tcnt++;
        if ((pclass[i]>=c.ambig*maxclass)
        &&(forced||(c.arpt[i]||!aused[c.aess[i]]))) {
          scnt++;
          if (besta<0) besta=i;
          else if (pclass[i]>pclass[besta]) besta=i;
        }
      }                             
                
    if ((pcnt>1)&&(tcnt==0)) {
      // did not understand question  
      besta=-1; 
      for (int j=0;j<c.acnt;j++)
        if (c.aname[j].toUpperCase().equals("DUH"))
          besta=j;
      if (besta<0) {
        System.out.println(now()+" No DUH answer found.");
        System.exit(1);
      }
    } // did not understand question   
    else if (scnt>1) { // ambiguous question
      for (int i=0;i<c.acnt;i++) {  
        pnext[i]=pclass[i]*pcont[i];
        if (aused[c.aess[i]]&&!c.arpt[i]) pnext[i]=0.0;
        if (!c.aofr[i]) pnext[i]=0.0;
      }
      findnextcand();
                                        
      besta=-1;
      for (int j=0;j<c.acnt;j++)
        if (c.aname[j].toUpperCase().equals("AMBIG"))
          besta=j;
      if (besta<0) {
        System.out.println(now()+" No AMBIG answer found.");
        System.exit(1);
      }
    } // multiple interpretations of question
    else if (scnt==0) {
      // rehash of old question
      for (int i=0;i<c.acnt;i++) {
        pnext[i]=pclass[i];
        if (!c.aofr[i]&&(i!=maxi)) pnext[i]=0.0;
      }
      findnextcand();
                                         
      besta=-1;                  
      for (int j=0;j<c.acnt;j++)
        if (c.aname[j].toUpperCase().equals("REHASH"))
          besta=j;
      if (besta<0) {
        System.out.println(now()+" No REHASH answer found.");
        System.exit(1);
      }
    } // rehash
    // else one viable, available match meets thresh


    // Do the expand mapping.
    if (besta==0) besta=nextans; 
    Element naen=c.aen[besta];

    // If this is a READBOOK command, re read The Book.
    // Used when book is replaced on a running system
    if (c.aname[besta].toUpperCase().equals("READBOOK")) newc=true;
      
    // Log the question
    if (c.logging
    &&(ques.indexOf("$$")!=0)
    &&(ques.trim().length()>0)) {
      String prev="?";
      if ((aseq!=null)&&(aseq.size()>0)) {
        int j=((Integer)aseq.elementAt(aseq.size()-1)).intValue();
        prev=c.aname[j];
      }
      logq(prev,c.aname[besta],ques);
    }
              
    last=false;
    wans=en2html(naen);

    pnext=flow();
    for (int i=0;i<pnext.length;i++) {
      if (aused[c.aess[i]]&&!c.arpt[i]) pnext[i]=0.0;
      if (!c.aofr[i]) pnext[i]=0.0;
    }
    findnextcand();
     
    // If no next answer, then append the "goodbye" answer
    if (nextcand.length==0) {
      int gbno=-1;
      for (int i=0;i<c.acnt;i++)
        if (c.aname[i].toUpperCase().equals("GOODBYE"))
          gbno=i;
      if (gbno>=0)
        wans+="<p/>\n"+en2html(c.aen[gbno]);
    } // if no answers to offer next                                       
    
    // Return that answer, and go home
    String sans=teacher.mysuppresshtml(wans);
    if (sans.length()>50)
      sans=sans.substring(0,45)+" ("+sans.length()+" chars)";
    System.out.println(now()+" => "+sans);

    // Show what we found
    if (false) {
      System.out.println(now()+"  pclass     pcont     pnext  Answer Name");
      for (int i=0;i<c.acnt;i++)
        System.out.println(now()
          +show(pclass[i]*100.0,7,1)+"%  "
          +show(pcont[i]*100.0,7,1)+"%  "
          +show(pnext[i]*100.0,7,1)+"%  "
          +c.aname[i]);
    }
             
  } // respond

  // Log a question and the answer we chose
  void logq(String p, String a, String q) {
    try {
      //RandomAccessFile raf=new RandomAccessFile(
      //  c.config.getServletContext().getRealPath(
      //  File.separator+"WEB-INF"+File.separator+"question.log"),
      //  "rw");
      //raf.skipBytes(1000000000);

      SimpleDateFormat fmt=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
      //raf.writeBytes(fmt.format(new Date())
      //  +","+hostname+","+p+","+a+","+q+"\n");
      //raf.close();
      System.out.println("logq: "+fmt.format(new Date())+","+hostname+","+p+","+a+","+q);
    }
    catch (Exception e) {
      System.out.println(now()+" Error writing log: "+e);
      e.printStackTrace();
    } // catch
  } // logq

  void findnextcand() {
      pnext[0]=0.0;

      // How many answers have nonzero probs?
      int nzn=0;
      for (int j=0;j<pnext.length;j++)
        if (pnext[j]>0.0) nzn++;
    
      // Sorted array of candidates
      nextcand=new int[nzn];
      nzn=0;
      for (int j=0;j<pnext.length;j++)
        if (pnext[j]>0.0) {
          nextcand[nzn]=j;
          nzn++;
        }
      for (int j=0;j<nzn-1;j++)
        for (int k=j+1;k<nzn;k++)
          if (pnext[nextcand[j]]<pnext[nextcand[k]]) {
            int n=nextcand[j];
            nextcand[j]=nextcand[k];
            nextcand[k]=n;
          }
                     
      // now nextcand[0] is best next, nextcand[1] 2nd, etc.
      double sump=0.0; 
      for (int j=0;j<nzn;j++) sump+=pnext[nextcand[j]];
      if (sump<=0.0)
        System.out.println(now()+" No \"next\" answers...?");
      else              
        for (int i=0;i<c.acnt;i++) pnext[i]/=sump;
  } // findnextcand


  String getans() {          
    return wans;
  } // getans


  String gettopic() {
    //System.out.println(now()+" Context is "+context);
    //System.out.println(now()+" Topic name "+c.aname[context]);
    return c.about[context];
  } // gettopic                 


  String getnext() {
    // Compute probs for all answers.
    // Initially, all answers created equal.
    String examples="";

    if (!last) {
      pnext=flow();
      for (int i=0;i<c.acnt;i++) {
        if (aused[c.aess[i]]&&!c.arpt[i]) pnext[i]=0.0;
        if (!c.aofr[i]) pnext[i]=0.0;
      }
      findnextcand();
                                          
      for (int j=0;(j<3)&&(j<nextcand.length);j++) {
        if (j==0) 
          examples="Or just press Enter to ask about ";
        else if (j==1) {
          if (nextcand.length==2) examples+=".  Another topic: ";
          else examples+=".  Some other topics: ";
        } // if second suggestion
        else examples+=", or ";
        examples+="<a href=\"?query=$$"
          +c.aname[nextcand[j]]
          +"&seq="+getseq()+"\">"
          +c.about[nextcand[j]]
          +"</a>";
      }   
      if (examples.length()>0) examples+="...";
    } // if not the last answer

    return examples;
  } // getnext              


  String getseq() {
    String seq="0";

    if ((nextcand!=null)&&(nextcand.length>0))
      seq=Integer.toString(nextcand[0]);

    if (aseq!=null)
      for (int i=0;i<aseq.size();i++)
        seq+="x"+Integer.toString(((Integer)aseq.elementAt(i)).intValue());

    return seq;
  } // 

                                     
  // Called to map an answer to HTML while doing right stuff
  String en2html(Element en) {
    // Handle last
    String slast=en.getAttribute("last");
    if (slast==null) slast="";
    slast=slast.trim().toUpperCase();
    if (slast.equals("YES")
    ||slast.equals("ON")
    ||slast.equals("1")
    ||slast.equals("TRUE"))
      last=true;

    // Get answer number, if any
    int ano=-1;
    String sname=en.getAttribute("topic");
    if (sname==null) sname="";
    sname=sname.trim().toUpperCase();
    if (!sname.equals("")) 
      for (int i=0;i<c.acnt;i++)
        if (sname.equals(c.aname[i].toUpperCase()))
          ano=i;



    if (ano>=0) {
      aseq.addElement(new Integer(ano));
      aused[ano]=true;
      aused[c.aess[ano]]=true;
      context=ano;
    }
           
    String s=html(en.getFirstChild());
    return s;
  } // en2html


  // Given a child node, turn this and adjacent kids to HTML.
  // Used for <expand> or <answer> expansion.
  // Ignore <next>, <expand>
  // Process <include>
  // Pass along everything else
  String html(Node n) {
    Node nn=n;
    String result="";
    while (nn!=null) {
      if (nn.getNodeName().equals("#text"))
        result+=nn.getNodeValue();
      else { // Element Node
        String ename=nn.getNodeName();
        if (ename.equals("include")) {
          Element ien=(Element)nn;
          int ano=-1;
          String sname=ien.getAttribute("answer");
          if (sname==null) sname="";
          sname=sname.trim().toUpperCase();
          if (!sname.equals("")) 
            for (int i=0;i<c.acnt;i++)
              if (sname.equals(c.aname[i].toUpperCase()))
                ano=i;

          if (ano==0) ano=nextcand[0];
          if (ano>0) result+=" "+en2html(c.aen[ano]);
        } // include                                
        else if (ename.equals("next")
               ||ename.equals("expand")
               ||ename.equals("else")) {
          result+=" ";
        } // ignore <next>, <expand>
        else if (ename.equals("random")) {
          Vector<Node> choices=new Vector<Node>();
          Node rkn=nn.getFirstChild();
          while (rkn!=null) {
            if (rkn.getNodeName().equals("choice")) 
              choices.addElement(rkn);
            rkn=rkn.getNextSibling();               
          } // while taking first pass at choices
          if (choices.size()==0) {
            System.out.println(now()+" <random> with no choices");
            System.exit(1);
          }
          double[] p=new double[choices.size()];
          for (int i=0;i<p.length;i++) {
            Element cen=(Element)choices.elementAt(i);
            String relprob=cen.getAttribute("relprob");
            if (relprob==null) relprob="";
            relprob=relprob.trim();
            if (relprob.equals("")) p[i]=1.0;
            else p[i]=Double.parseDouble(relprob);
          }
          double sump=0.0;
          for (int i=0;i<p.length;i++) sump+=p[i];
          if (sump<=0.0) {
            System.out.println(now()+" Relative probabilities goofed up in <random>");
            System.exit(1);
          }
          double r=0.999999*sump*Math.random();
          int i=0;
          sump=p[0];
          while (r>sump) {i++; sump+=p[i];}
          result+=" "+html(choices.elementAt(i).getFirstChild());
        } // random
        else if (ename.equals("btw")) {
          Element en=(Element)nn;
          String top=en.getAttribute("topic");

          int ano=-1;
          if (top==null) top="";
          top=top.trim().toUpperCase();
          if (!top.equals("")) 
            for (int i=0;i<c.acnt;i++)
              if (top.equals(c.aname[i].toUpperCase()))
                ano=i;

          if (ano<0) 
            System.out.println(now()+" Invalid <btw> target:  "+top);
          else {
            Node kidn=en.getFirstChild();
            if (!aused[c.aess[ano]]||c.arpt[ano])
              result+=" "+html(kidn);
            else {
              while (kidn!=null) {
                if (kidn.getNodeName().equals("else")) {
                  result+=" "+html(kidn.getFirstChild());
                  kidn=null;
                }
                else kidn=kidn.getNextSibling();
              } // look for else
            } // btw topic already used
          } // found btw topic                                
        } // handle btw
        else if (ename.equals("answercount")) {
          int i=0;
          Node nx=c.root.getFirstChild();
          while (nx!=null) {
            if (nx.getNodeName().equals("answer")) i++;
            nx=nx.getNextSibling();
          }
          result+=i;
        } // handle answercount
        else if (ename.equals("questioncount")) {
          int i=0;
          Node nx=c.root.getFirstChild();
          while (nx!=null) {
            if (nx.getNodeName().equals("question")) i++;
            nx=nx.getNextSibling();
          }
          result+=i;
        } // handle questioncount
        else if (ename.equals("nexttopic")) {
          Element en=(Element)nn;

          String numstr=en.getAttribute("num");
          if (numstr==null) numstr="";
          numstr=numstr.trim();
          int num=0;
          if (!numstr.equals("")) num=Integer.parseInt(numstr);

          boolean link=true;
          String linkstr=en.getAttribute("link");
          if (linkstr==null) linkstr="";
          linkstr=linkstr.trim().toUpperCase();
          if (linkstr.equals("OFF")||linkstr.equals("NO")
          ||linkstr.equals("0")||linkstr.equals("FALSE"))
            link=false;
           
          if (link) {
            result+="<a href=\"?query=$$"
              +c.aname[nextcand[num]]
              +"&seq="+getseq()+"\">"
              +c.about[nextcand[num]]+"</a>";
          }
          else
            result+=c.about[nextcand[num]];
        } // mention next topic
        else if (ename.equals("quest")) {
          result+=ques;
        } // user's last question
        else { // some other element; copy it
          String tag=nn.getNodeName();
          result+="<"+tag;
        
          Element en=(Element)nn;
          NamedNodeMap nnm=en.getAttributes();
          for (int i=0;i<nnm.getLength();i++) {
            Node an=nnm.item(i);
            String attr=an.getNodeName();
            String value=an.getNodeValue();

            if (tag.equals("a")&&attr.equals("href")
            &&(value.indexOf("?")==0))
              value+="&seq="+getseq();

            result+=" "+attr+"=\""+value+"\"";
          }

          Node cn=en.getFirstChild();
          if (cn!=null)
            result+=">"+html(cn)+"</"+tag+">";
          else
            result+="/>";
        } // some other element
      } // Element node                
      nn=nn.getNextSibling();
    }

    return result;
  } // html

  //
  // This guy gives you "probabilities" of where conversation will flow.
  //
  // As a starting point, we use leadtopics... a prioritized
  // list of topics that can be evoked directly by a relevant
  // question.
  //
  // Then, we overlay on top of this the most recent next="..."
  // flow hint.  Anything here shows up higher than things
  // in the leadtopics ones.  In this way, we have a priority
  // assigned to every valid answer topic in this context.
  //
  // Note that this does NOT consider what answers have already
  // been given to eliminate topics.  This information is used later...
  //
  double[] flow() {

    double[] f=new double[c.acnt];
    for (int i=0;i<c.acnt;i++) f[i]=0.0;
    for (int i=0;i<c.ltops.length;i++) f[c.ltops[i]]=c.ltops.length-i;
                                
    int[] nxt=null;
    for (int i=0;i<aseq.size();i++) {
      int j=((Integer)aseq.elementAt(i)).intValue();
      if (c.anext[j]!=null) nxt=c.anext[j];
    }

    if (nxt!=null) 
      for (int i=0;i<nxt.length;i++)
        f[nxt[i]]=c.ltops.length+nxt.length-i;
       
    // Normalize
    double sump=0.0; int pluscnt=0;
    for (int i=0;i<c.acnt;i++) sump+=f[i]; 
    for (int i=0;i<c.acnt;i++) f[i]/=sump;  

    return f;
  } // flow

  String now() {
    return DateFormat
           .getTimeInstance(DateFormat.MEDIUM)
           .format(new java.util.Date());
  } // now
                     
  public static String show(double d) {
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

  public static String show(double dd, int i) {
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

  public static String show(double d, int i, int j) {
    String s=show(d,j);
    if (s.length()>=i) return s;
    while (s.length()<i) s=" "+s;
    return s;
  } // show in i-char field with j dec places



} // chatstate
