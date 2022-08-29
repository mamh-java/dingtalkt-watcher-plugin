package org.jenkinsci.plugins.dingtalkwatcher;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.listeners.ItemListener;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Stack;


@Extension
public class WatcherItemListener extends ItemListener {

    private final DingtalkWatcher dingtalk;
    private final String jenkinsRootUrl;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public WatcherItemListener() {
        this(new DingtalkWatcher(Jenkins.get()), Jenkins.get().getRootUrl());
    }

    public WatcherItemListener(final DingtalkWatcher dingtalk, final String jenkinsRootUrl) {
        if (dingtalk == null) throw new IllegalArgumentException(
                "No dingtalk provided"
        );

        this.dingtalk = dingtalk;
        this.jenkinsRootUrl = jenkinsRootUrl;
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        if (!(item instanceof Job<?, ?>)) return;
        final Job<?, ?> job = (Job<?, ?>) item;
        getNotification().subject("renamed from " + oldName).send(job);
    }

    @Override
    public void onUpdated(Item item) {
        if (!(item instanceof Job<?, ?>)) return;
        getNotification().subject("updated").send(item);
    }

    @Override
    public void onDeleted(Item item) {
        if (!(item instanceof Job<?, ?>)) return;
        getNotification().subject("deleted").send(item);
    }

    private Notification.Builder getNotification() {
        return new Notification.Builder(dingtalk, jenkinsRootUrl);
    }

    private static class Notification extends DingtalkWatcherNotification {

        private final @Nonnull
        Job<?, ?> job;

        public Notification(final Builder builder) {

            super(builder);
            job = builder.job;
        }

        @Override
        protected String getSubject() {
            return String.format("Job %s %s", getName(), super.getSubject());
        }

        @Override
        protected @Nonnull
        Map<String, String> pairs() {
            final Map<String, String> pairs = super.pairs();
            final String historyUrl = dingtalk.configHistory().lastChangeDiffUrl(job);
            if (historyUrl != null) {
                String url = dingtalk.absoluteUrl(historyUrl).toString();
                pairs.put("Change", "[show Diff ](" + url + ")");
            }
            return pairs;
        }

        private static class Builder extends DingtalkWatcherNotification.Builder {

            private Job<?, ?> job;

            public Builder(final DingtalkWatcher dingtalk, final String jenkinsRootUrl) {
                super(dingtalk, jenkinsRootUrl);
            }

            @Override
            public void send(final Object o) {
                job = (Job<?, ?>) o;

                final WatcherJobProperty property = job.getProperty(WatcherJobProperty.class);

                if (property == null) {
                    return;
                }
                this.recipients(property.getMention());
                this.webhookurl(property.getWebhookurl());

                Stack<String> stack = new Stack<String>();
                stack.push(job.getShortUrl());
                ItemGroup parent = job.getParent();
                while (parent != null && parent instanceof Item) {
                    Item item = (Item) parent;
                    stack.push(item.getShortUrl());
                    parent = item.getParent();
                }
                StringBuilder urlPath = new StringBuilder();
                while (!stack.isEmpty()) {
                    urlPath.append(stack.pop());
                }
                url(urlPath.toString());
                name(job.getFullDisplayName());

                new Notification(this).send();
            }
        }
    }
}
