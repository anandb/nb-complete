package github.anandb.netbeans.model;

public class SessionItem {
    private final Session session;
    private final String title;

    public SessionItem(Session session, String title) {
        this.session = session;
        this.title = title;
    }

    public Session getSession() {
        return session;
    }

    @Override
    public String toString() {
        String projectName = session.projectName();
        if (projectName != null && !projectName.isEmpty()) {
            return "[" + projectName + "] " + title;
        }
        return title;
    }
}
