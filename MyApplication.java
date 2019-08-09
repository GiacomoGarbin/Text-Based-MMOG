import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/")
public class MyApplication extends Application{
    @Override
    public Set<Class<?>> getClasses() {
        HashSet set = new HashSet<Class<?>>();
        set.add( ServerREST.class );
        return set;
    }
}