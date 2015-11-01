package org.jenkinsci.plugins.walti;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Messages;
import hudson.util.Secret;
import io.walti.api.Plugin;
import io.walti.api.Scan;
import io.walti.api.Target;
import io.walti.api.WaltiApi;
import io.walti.api.exceptions.WaltiApiException;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.*;

/**
 * Call Walti API to execute security scan
 *
 * @author Sho Matsumoto
 */
public class WaltiScanner extends Recorder implements Serializable {

    private static final int POLLING_INTERVAL = 10;

    private final String key;
    private final Secret secret;
    private final String target;
    private final List<String> selectedPlugins;
    private final boolean noWait;
    private final boolean unstablePreferred;

    @DataBoundConstructor
    public WaltiScanner(String key, String secret, String target, JSONArray plugins, boolean noWait, boolean unstablePreferred) {
        this.key = key;
        this.secret = Secret.fromString(secret);
        this.target = target;
        this.selectedPlugins = new ArrayList<String>();
        Collection<String> col = JSONArray.toCollection(plugins, ArrayList.class);
        this.selectedPlugins.addAll(col);
        this.noWait = noWait;
        this.unstablePreferred = unstablePreferred;
    }

    /**
     * Get API key
     *
     * @return API key
     */
    public String getKey() {
        return key;
    }

    /**
     * Get API Secret
     *
     * @return API secret
     */
    public Secret getSecret() {
        return secret;
    }

    /**
     * Get target name
     *
     * @return target name
     */
    public String getTarget() { return target; }

    /**
     * Whether wait until queued scans are completed
     *
     * @return return true if it is not necessary to wait
     */
    public boolean isNoWait() { return noWait; }


    /**
     * Whether regard build result as UNSTABLE instead of FAILURE when scan result is not OK
     *
     * @return return true if build result is regarded as UNSTABLE
     */
    public boolean isUnstablePreferred() { return unstablePreferred; }

    /**
     * Get plugin names to execute
     *
     * @return plugin names
     */
    public List<String> getSelectedPlugins() { return selectedPlugins; }

    public String getSelectedPluginsString() {
        List<String> pluginList = getSelectedPlugins();
        return StringUtils.join(pluginList, ',');
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException {
        PrintStream logger = listener.getLogger();
        WaltiApi api = WaltiApi.createInstance(key, secret.getPlainText());
        Set<String> selectedPlugins = new HashSet<String>(getSelectedPlugins());

        Scan.QueueResult queueResult = Scan.QueueResult.UNDEFINED;
        for (String plugin : getSelectedPlugins()) {
            try {
                queueResult = Scan.queue(api, target, plugin);
            } catch (WaltiApiException e) {
                build.setResult(Result.FAILURE);
                logger.println(plugin + "のキュー登録に失敗しました");
                e.printStackTrace(logger);
                logger.close();
                return true;
            }

            switch (queueResult) {
                case SUCCESS:
                    logger.println(plugin + "のキューを登録しました");
                    break;
                case SKIPPED:
                    selectedPlugins.remove(plugin);
                    build.setResult(Result.UNSTABLE);
                    logger.format("決済情報が登録されていないか、すでにスキャン実行中のため%sのキュー登録をスキップします\n", plugin);
                    break;
                default:
                    build.setResult(Result.FAILURE);
                    logger.println("スキャンキューの登録に失敗");
                    break;
            }
        }

        if (selectedPlugins.isEmpty()) {
            logger.println("キュー登録がすべてスキップされました");
            logger.close();
            return true;
        }

        if (isNoWait()) {
            logger.println("スキャンの完了を待たない設定のため結果取得をスキップしました");
            logger.close();
            return true;
        }

        logger.println("スキャン結果をポーリングしています");

        int i = 0;
        while (!selectedPlugins.isEmpty()) {
            if (shouldOutputPolling(i)) {
                logger.println(".");
            }
            i++;
            Target targetObj = null;
            try {
                targetObj = Target.find(api, target);
            } catch (WaltiApiException e) {
                build.setResult(Result.FAILURE);
                logger.println("スキャン結果ポーリング時の応答が異常");
                e.printStackTrace(logger);
                logger.close();
                return true;
            }

            for (Plugin pluginObj : targetObj.getPlugins()) {
                if (pluginObj.isQueued() || !selectedPlugins.contains(pluginObj.getName())) {
                    // スキャンがまだ終わっていないもしくは選択されていないプラグインの結果表示は不要
                    continue;
                }
                selectedPlugins.remove(pluginObj.getName());

                try {
                    Scan scan = pluginObj.getScan();
                    switch (scan.getResultStatus()) {
                        case Scan.RESULT_OK:
                            logger.println(pluginObj.getName() + "のスキャンが完了しました。結果:" + scan.getStatus() + " メッセージ:" + scan.getMessage());
                            logger.println(targetObj.getResultURL(pluginObj.getName()));
                            build.setResult(judgeResult(scan.getStatusColor()));
                            break;
                        default:
                            logger.println(pluginObj.getName() + "のスキャンが中断されました。以下のURLから詳細を確認してください");
                            logger.println(targetObj.getResultURL(pluginObj.getName()));
                            build.setResult(Result.FAILURE);
                            logger.close();
                            return true;
                    }
                } catch (WaltiApiException e) {
                    build.setResult(Result.FAILURE);
                    logger.println("スキャン結果URLの取得に失敗");
                    e.printStackTrace(logger);
                    logger.close();
                    return true;
                }
            }
            try {
                Thread.sleep(POLLING_INTERVAL * 1000);
            } catch (InterruptedException e) {
                build.setResult(Result.ABORTED);
                logger.println("結果のポーリングを中止します。スキャン自体のキャンセルはWalti.ioのターゲット画面から行ってください");
                logger.close();
                return true;
            }
        }
        logger.close();
        return true;
    }

    /**
     * Judge if polling progress should be outputted
     *
     * @param count
     * @return
     */
    private boolean shouldOutputPolling(int count) {
        int outputInterval = (int)Math.ceil(60.0 / POLLING_INTERVAL);
        return count % outputInterval == 0;
    }

    /**
     * Judge build result from scan status color
     *
     * @param statusColor
     * @return build result
     */
    private Result judgeResult(String statusColor) {
        if (Scan.STATUS_COLOR_GREEN.equals(statusColor)) {
            return Result.SUCCESS;
        }
        if (Scan.STATUS_COLOR_GREY.equals(statusColor)) {
            return Result.UNSTABLE;
        }
        if (isUnstablePreferred()) {
            return Result.UNSTABLE;
        }
        return Result.FAILURE;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public String getDisplayName() {
            return "Waltiでスキャンを実行";
        }

        public FormValidation doCheckKey(@QueryParameter String key, @QueryParameter String secret) {
            if (key.isEmpty()) {
                return FormValidation.error(Messages.FormValidation_ValidateRequired());
            }
            if (secret.isEmpty()) {
                return FormValidation.ok();  // まだAPIキーが入力されていない場合は何もしない
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckSecret(@QueryParameter String key, @QueryParameter String secret) {
            if (secret.isEmpty()) {
                return FormValidation.error(Messages.FormValidation_ValidateRequired());
            }
            if (key.isEmpty()) {
                return FormValidation.ok();  // まだAPIキーが入力されていない場合は何もしない
            }

            WaltiApi api = WaltiApi.createInstance(key, Secret.fromString(secret).getPlainText());
            try {
                if (!api.isValidCredentials()) {
                    return FormValidation.error("APIキーまたはシークレットが正しくありません。");
                }
                return FormValidation.ok();
            } catch (WaltiApiException e) {
                e.printStackTrace();
                return FormValidation.error("予期せぬエラーが発生しました " + e.getMessage());
            }
        }

        public ListBoxModel doFillTargetItems(@QueryParameter String key, @QueryParameter String secret) {
            ListBoxModel m = new ListBoxModel();
            if (key.isEmpty() || secret.isEmpty()) {
                // キーかシークレットが空の場合は空リスト
                return m;
            }

            WaltiApi api = WaltiApi.createInstance(key, Secret.fromString(secret).getPlainText());
            try {
                List<Target> targets = Target.getAll(api);
                for (Target target : targets) {
                    m.add(target.getName(), target.getName());
                }
            } catch (WaltiApiException e) {
                // do nothing
            } catch (Exception e) {
                e.printStackTrace();
            }
            return m;
        }

        public CheckBoxModel doFillPluginsItems(@QueryParameter String key, @QueryParameter String secret, @QueryParameter String target) {
            // ターゲット情報
            WaltiApi api = WaltiApi.createInstance(key, Secret.fromString(secret).getPlainText());
            CheckBoxModel model = new CheckBoxModel();

            if (target.isEmpty()) {
                return model;
            }

            try {
                Target targetObj = Target.find(api, target);
                for (Plugin plugin : targetObj.getPlugins()) {
                    model.add(new CheckBoxModel.Item(plugin.getName()));
                }
                return model;
            } catch (WaltiApiException e) {
                // do nothing
            } catch (Exception e) {
                e.printStackTrace();
            }
            return model;
        }
    }
}
