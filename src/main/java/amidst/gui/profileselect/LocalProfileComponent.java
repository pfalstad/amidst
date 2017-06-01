package amidst.gui.profileselect;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import amidst.Application;
import amidst.documentation.AmidstThread;
import amidst.documentation.CalledOnlyBy;
import amidst.documentation.NotThreadSafe;
import amidst.logging.AmidstLogger;
import amidst.logging.AmidstMessageBox;
import amidst.mojangapi.LauncherProfileRunner;
import amidst.mojangapi.RunningLauncherProfile;
import amidst.mojangapi.file.LauncherProfile;
import amidst.mojangapi.file.UnresolvedLauncherProfile;
import amidst.mojangapi.file.VersionListProvider;
import amidst.mojangapi.minecraftinterface.local.LocalMinecraftInterfaceCreationException;
import amidst.parsing.FormatException;
import amidst.threading.WorkerExecutor;

@NotThreadSafe
public class LocalProfileComponent extends ProfileComponent {
	private final Application application;
	private final WorkerExecutor workerExecutor;
	private final LauncherProfileRunner launcherProfileRunner;
	private final UnresolvedLauncherProfile unresolvedProfile;

	private volatile boolean isResolving = false;
	private volatile boolean failedResolving = false;
	private volatile boolean isLoading = false;
	private volatile boolean failedLoading = false;
	private volatile LauncherProfile resolvedProfile;

	@CalledOnlyBy(AmidstThread.EDT)
	public LocalProfileComponent(
			Application application,
			WorkerExecutor workerExecutor,
			LauncherProfileRunner launcherProfileRunner,
			UnresolvedLauncherProfile unresolvedProfile) {
		this.application = application;
		this.workerExecutor = workerExecutor;
		this.launcherProfileRunner = launcherProfileRunner;
		this.unresolvedProfile = unresolvedProfile;
		initComponent();
		resolveLater();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private void resolveLater() {
		isResolving = true;
		repaintComponent();
		workerExecutor.run(this::tryResolve, this::resolveFinished);
	}

	@CalledOnlyBy(AmidstThread.WORKER)
	private boolean tryResolve() {
		try {
			resolvedProfile = unresolvedProfile.resolve(VersionListProvider.getRemoteOrLocalVersionList());
			return true;
		} catch (FormatException | IOException e) {
			AmidstLogger.warn(e);
			return false;
		}
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private void resolveFinished(boolean isSuccessful) {
		isResolving = false;
		failedResolving = !isSuccessful;
		repaintComponent();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	public void load() {
		isLoading = true;
		repaintComponent();
		workerExecutor.run(this::tryLoad, this::loadFinished);
	}

	@CalledOnlyBy(AmidstThread.WORKER)
	private Optional<RunningLauncherProfile> tryLoad() {
		// TODO: Replace with proper handling for modded profiles.
		try {
			AmidstLogger.info(
					"using minecraft launcher profile '" + getProfileName() + "' with versionId '" + getVersionName()
							+ "'");

			String possibleModProfiles = ".*(optifine|forge).*";
			if (Pattern.matches(possibleModProfiles, getVersionName().toLowerCase(Locale.ENGLISH))) {
				AmidstLogger.error(
						"Amidst does not support modded Minecraft profiles! Please select or create an unmodded Minecraft profile via the Minecraft Launcher.");
				AmidstMessageBox.displayError(
						"Error",
						"Amidst does not support modded Minecraft profiles! Please select or create an unmodded Minecraft profile via the Minecraft Launcher.");
				return Optional.empty();
			}

			return Optional.of(launcherProfileRunner.run(resolvedProfile));
		} catch (LocalMinecraftInterfaceCreationException e) {
			AmidstLogger.error(e);
			AmidstMessageBox.displayError("Error", e);
			return Optional.empty();
		}
	}

	@CalledOnlyBy(AmidstThread.EDT)
	private void loadFinished(Optional<RunningLauncherProfile> runningLauncherProfile) {
		isLoading = false;
		failedLoading = !runningLauncherProfile.isPresent();
		repaintComponent();
		if (runningLauncherProfile.isPresent()) {
			application.displayMainWindow(runningLauncherProfile.get());
		}
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected boolean isResolving() {
		return isResolving;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected boolean failedResolving() {
		return failedResolving;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected boolean isLoading() {
		return isLoading;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected boolean failedLoading() {
		return failedLoading;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected boolean isReadyToLoad() {
		return !isResolving && !failedResolving;
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected String getProfileName() {
		return unresolvedProfile.getName();
	}

	@CalledOnlyBy(AmidstThread.EDT)
	@Override
	protected String getVersionName() {
		if (isReadyToLoad()) {
			return resolvedProfile.getVersionId();
		} else {
			return "";
		}
	}
}
