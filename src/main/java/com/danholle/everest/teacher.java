package com.danholle.everest;
 
//
// ----------------------------------------------------------------
//
// The Teacher Servlet
//
// ----------------------------------------------------------------
//


import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.text.*;
   
@SuppressWarnings("serial")
public class teacher extends HttpServlet {
  ServletConfig config;

  String book; // File name of the "book"
  boolean logging; // true if question logging is on
  chatengine chatter;

  @Override
  public void init(ServletConfig cfg) throws ServletException {
    config=cfg;

    book="book.xml";
        
    String slog=cfg.getInitParameter("logging");
    if (slog==null) slog="";
    slog=slog.trim().toUpperCase();
    if (slog.equals("NO")||slog.equals("FALSE")
    ||slog.equals("OFF")||slog.equals("0"))
      logging=false;
    else
      logging=true;
       
                    
    log(now()+" Starting Chat Engine defined in "+book);

    chatter=new chatengine(cfg,book,logging);
                              
    String msg=chatter.botname+" is ready to chat.";
    log(now()+" "+msg);
  } // init

  public void log(String s) {
    config.getServletContext().log(s);
  } // log
                
  static String now() {
    return DateFormat
           .getTimeInstance(DateFormat.MEDIUM)
           .format(new java.util.Date());
  } // now
                  

  public void doGet(HttpServletRequest request,
                    HttpServletResponse response)
  throws IOException, ServletException {

    // Get ready to start sending HTML
    response.setContentType("text/html");
    PrintWriter writer = response.getWriter();

    String query=request.getParameter("query");
    if (query==null) query="";
    query=mycleanuphttp(query.trim().toUpperCase());

    String seq=request.getParameter("seq");
    if (seq==null) seq="";
    seq=seq.trim();

    log(now()+" doGet:  query='"+query+", seq='"+seq+"'");

    if (seq.equals("")&&query.equals("")) query="$$intro";
          
    // Get response, so we know what we are talking "ABOUT"
    chatstate cs=new chatstate(chatter,request.getRemoteHost());
    cs.setques(query);
    cs.setseq(seq);
    cs.respond();

    String resp=cs.getans();
                                      
    // Spit out header HTML
    BufferedReader headin=new BufferedReader(
      new FileReader(config.getServletContext().getRealPath(File.separator
      +"WEB-INF"+File.separator+"header.html")));

    String topic=cs.gettopic();
    String examples=cs.getnext();
    String newseq=cs.getseq();

    String ss;
    while ((ss=headin.readLine())!=null) {
      ss=myreplace("$TOPIC",topic,ss);
      ss=myreplace("$EXAMPLES",examples,ss);
      writer.println(ss);
    }
    headin.close();
 

    // Spit out answer HTML
    writer.println("<BR CLEAR=ALL/>\n"+resp+"<P>");

    // Spit out form for user to give next question
    if (examples.length()>0)
      writer.println(
            "<FORM METHOD=\"GET\" ACTION=\"\">\n"+
            "<BR CLEAR=ALL/>\n"+
            "<FONT FACE=\"Helvetica, Arial\"><b>Ask a question: </b>&nbsp;&nbsp;\n"+
            "<INPUT TYPE=\"text\" NAME=\"query\" SIZE=\"40\">\n"+
            "<INPUT TYPE=\"submit\" NAME=\"submit\" VALUE=\"Reply\">\n"+
            "<INPUT TYPE=\"hidden\" NAME=\"seq\" VALUE=\""+newseq+"\">\n"+
            "<INPUT TYPE=\"reset\" NAME=\"name\" VALUE=\"Reset\">\n"+
            "</FORM>");
         
    // Spit out trailer HTML
    BufferedReader trailin=new BufferedReader(
      new FileReader(config.getServletContext().getRealPath(File.separator
      +"WEB-INF"+File.separator+"trailer.html")));
    while ((ss=trailin.readLine())!=null) {
      ss=myreplace("$TOPIC",topic,ss);
      ss=myreplace("$EXAMPLES",examples,ss);
      writer.println(ss);      
    }
    trailin.close();
             
  } // doget



  static String http_reverse_subst[][] = {
    {"%","%25"},
    {"\"","%22"},
    {"\\","%2F"},
    {" ","%20"},
    {"!","%21"},
    {".","%22"},
    {"?","%23"},
    {"$","%24"},
    {"&","%26"},
    {"'","%27"},
    {"(","%28"},
    {")","%29"},
    {"+","%2B"},
    {",","%2C"},
    {":","%3A"},
    {",","%3B"},
    {"<","%3C"},
    {"=","%3D"},
    {">","%3E"},
    {"?","%3F"}};


  static String http_subst[][] = {
    {"%0D"," "},
    {"%0A"," "},
    {"%20"," "},
    {"%21","!"},
    {"%22","\""},
    {"%23","?"},
    {"%24","$"},
    {"%25","%"},
    {"%26","&"},
    {"%27","'"},
    {"%28","("},
    {"%29",")"},
    {"%2A"," "},
    {"%2B","+"},
    {"%2C",","},
    {"%2D","-"},
    {"%2E","."},
    {"%2F","\\"},
    {"%2a"," "},
    {"%2b","+"},
    {"%2c",","},
    {"%2d","-"},
    {"%2e","."},
    {"%2f","\\"},
    {"%3A",":"},
    {"%3B",","},
    {"%3C","<"},
    {"%3D","="},
    {"%3E",">"},
    {"%3F","?"}, 
    {"%3a",":"},
    {"%3b",","},
    {"%3c","<"},
    {"%3d","="},
    {"%3e",">"},
    {"%3f","?"}, 
    {"%40","@"},
    {"%5B"," "},
    {"%5C"," "},
    {"%5D","]"},
    {"%5E","^"},
    {"%5F","_"},
    {"%60","`"},
    {"%7B","{"},
    {"%7C","|"},
    {"%7D","}"},
    {"%7E","~"},
    {"%92","'"},
    {"%B4"," "},
    {"%E9"," "},
    // German characters:
    {"%C4","Ae"},
    {"%E4","ae"},
    {"%D6","Oe"},
    {"%F6","oe"},
    {"%DC","Ue"},
    {"%FC","ue"},
    {"%DF","ss"},

    {"HTTP/1.0"," "},
    {"HTTP/1.1"," "},
    {"404"," "},
    {"206"," "},
    {"text=",""},
    {"virtual=none",""},
    {"submit"," "},
    {"=Reply"," "}
  }; // http_subst

     



  static String myreplace(String substx, String substy, String norm) {
    int index = -1;
    if (substx.compareTo(substy)==0) return norm;
    try {
      if (substx.length() > 0) {
        int len = substx.length();
        while ((index = norm.indexOf(substx)) >= 0) {
          if (index > norm.length()) {
              //System.out.println("'"+substx+"' '"+substy+"' '"+norm+"'"+index);
            return (norm);
          }
          String head = norm.substring(0, index);
          String tail = 
            (index+len < norm.length()) ? norm.substring(index + len) : "";
          norm = head + substy + tail;
        }
      }
    }
    catch (Exception e) {
      System.out.println("Substituter Exception "+e); 
      System.out.println("'"+substx+"' '"+substy+"' '"+norm+"'"+index);
    }
    return norm;
  } // replace (3 args)

  static String myreplace(String[][] subst, String input) {
    input = input.trim();
    input = " "+input+" ";
    for (int i = 0; i < subst.length; i++)
      input = myreplace(subst[i][0], subst[i][1], input);
    input = input.trim();
    return input;
  } // replace (2 args)

  static String mycleanuphttp(String arg) {
    arg = arg.replace('+',' ');
    if (arg.indexOf("HTTP/") >= 0) 
      arg = arg.substring(0, arg.indexOf("HTTP/"));
    arg = myreplace(http_subst, arg);
    return(arg);
  } // mycleanuphttp


  static String format_http(String arg) {
    arg = myreplace(http_reverse_subst, arg);
    return(arg);
  } // format_http

  static String mysuppresshtml(String line) {
    line = myreplace("&nbsp;"," ",line);
    line = myreplace("<BR>","\n",line);
    line = myreplace("<br>","\n",line);
    line = myreplace("</li>","\n",line);
    line = myreplace("</LI>","\n",line);
    while (line.indexOf('<') >= 0) {
	//       System.out.println("Line = "+line);
       int start = line.indexOf('<');
       int end = start;
       if (line.indexOf('>') > end) end = line.indexOf('>');
       String tail;
       if (end+1 >= line.length()) tail = ""; // when ">" is last char
       else tail = line.substring(end+1); // ordinary case
       line = line.substring(0,start) + tail;
    }
    // System.out.println("Return Line = "+line);
    line = myreplace("\n\n","\n",line);
    line = myreplace("  "," ",line);
    line = myreplace("&gt;",">",line);
    line = myreplace("&lt;","<",line);
    while (line.startsWith("\n")) line = line.substring(1, line.length()); 
    return line;
  } // suppresshtml

} // teacher       
