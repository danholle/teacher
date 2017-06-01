# Minimal Web App -- Google App Engine Standard Environment

This sample demonstrates how to deploy an application on Google App Engine.

See the [Google App Engine standard environment documentation][ae-docs] for more
detailed instructions.

[ae-docs]: https://cloud.google.com/appengine/docs/java/

## To try it: 

1. Create a project by visiting appengine.google.com.  If you call it "blah", you'll 
   get a project name that might be something like blah-123456.

1. Update the `<application>` tag in `src/main/webapp/WEB-INF/appengine-web.xml`
   with the aforementioned project name.

1. Run locally with Jetty.  (Hit webapp via http://localhost:8080)

    $ mvn appengine:devserver

1. Deploy to Google App Engine.  (Hit webapp via https://<project-name>.appspot.com)
 
    $ mvn appengine:update


