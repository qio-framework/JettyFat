package fat.web;

import qio.annotate.HttpHandler;
import qio.annotate.verbs.Get;
import qio.model.web.ResponseData;

@HttpHandler
public class HelloHandler {

    @Get("/")
    public String hello(ResponseData data){
        data.set("message", "made it!");
        return "index.jsp";
    }

}
