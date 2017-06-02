# teacher

This webapp presents the browser-based "student" with a dialog with a server-based "teacher".  The user asks questions or
mentions topics;  the teacher then presents a page on that topic, and finishes by prompting the user with possible next 
questions or topics that might logically follow.

This is useful for presenting material where there are many different possible topics, only a subset of which may
be of interest to the student.  The source includes an example teacher who is a virtual climber on the north side
of Everest, which the student can "talk to" about a range of topics relating to the Everest climbing experience.

To define your own teacher in a particular domain,
 1.  Identify a number of topics in your domain (perhaps a dozen or more), where each topic can be 
     presented in a page or so.
 2.  Come up with some example questions that the student might use to train the teacher to map
     questions to topical pages.
 3.  Put this material into the format found in WEB-INF/book.xml (where the example teacher is defined).
 4.  Fire it up and try it out.

I need to write a lot more here;  but this will do for starters.


## Notes to myself

To get this thing running on Google App Engine:

1. Create a project by visiting appengine.google.com.  If you call it "blah", you'll 
   get a project name that might be something like blah-123456.
2. Update the `<application>` tag in `src/main/webapp/WEB-INF/appengine-web.xml`
   with the aforementioned project name.
3. Run locally with Jetty.  (Hit webapp via http://localhost:8080)

    $ mvn appengine:devserver

4. Deploy to Google App Engine.  (Hit webapp via https://<project-name>.appspot.com)
 
    $ mvn appengine:update

That's all, folks...


