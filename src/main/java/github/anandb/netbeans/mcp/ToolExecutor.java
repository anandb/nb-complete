package github.anandb.netbeans.mcp;

public abstract class ToolExecutor<T, R> {
    private final Class<T> argClass;

    protected ToolExecutor(Class<T> argClass) {
        this.argClass = argClass;
    }

    public Class<T> getArgClass() {
        return argClass;
    }

    abstract R execute(T arguments) throws Exception;
}
