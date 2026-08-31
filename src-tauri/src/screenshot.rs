#[cfg(target_os = "linux")]
mod platform {
    use anyhow::{Context, Result};
    use ashpd::desktop::{
        screencast::{CursorMode, Screencast, Stream},
        PersistMode,
    };
    use std::fs;
    use std::os::fd::{IntoRawFd, OwnedFd};
    use std::os::unix::process::CommandExt;
    use std::process::Command;

    pub struct Screenshot {
        pub filename: String,
        pub bytes: Vec<u8>,
    }

    pub struct ScreenshotSession {
        _portal: Screencast<'static>,
        _session: ashpd::desktop::Session<'static, Screencast<'static>>,
        remote: OwnedFd,
        streams: Vec<Stream>,
    }

    impl ScreenshotSession {
        pub async fn start() -> Result<Self> {
            let portal = Screencast::new()
                .await
                .context("Could not connect to xdg-desktop-portal ScreenCast")?;
            let session = portal
                .create_session()
                .await
                .context("Could not create the Wayland screen-sharing session")?;
            portal
                .select_sources(
                    &session,
                    CursorMode::Embedded,
                    ashpd::desktop::screencast::SourceType::Monitor.into(),
                    true,
                    None,
                    PersistMode::DoNot,
                )
                .await
                .context("Could not request monitor selection from xdg-desktop-portal")?;
            let response = portal
                .start(&session, None)
                .await
                .context("Could not open the Wayland monitor-sharing dialog")?
                .response()
                .context("Wayland monitor sharing was cancelled")?;
            let streams = response.streams().to_vec();
            if streams.is_empty() {
                anyhow::bail!("No monitors were selected for the screenshot workflow step.");
            }
            let remote = portal
                .open_pipe_wire_remote(&session)
                .await
                .context("Could not open the PipeWire screen-sharing stream")?;

            Ok(Self {
                _portal: portal,
                _session: session,
                remote,
                streams,
            })
        }

        pub async fn capture(&self) -> Result<Vec<Screenshot>> {
            let remote = self
                .remote
                .try_clone()
                .context("Could not duplicate the PipeWire screen-sharing handle")?;
            let streams = self.streams.clone();
            tokio::task::spawn_blocking(move || capture_all(remote, &streams))
                .await
                .context("Screenshot capture worker stopped unexpectedly")?
        }

        pub async fn close(self) {
            if let Err(error) = self._session.close().await {
                tracing::warn!("Could not close Wayland screen-sharing session: {error}");
            }
        }
    }

    fn capture_all(remote: OwnedFd, streams: &[Stream]) -> Result<Vec<Screenshot>> {
        streams
            .iter()
            .enumerate()
            .map(|(index, stream)| capture_one(&remote, stream, index))
            .collect()
    }

    fn capture_one(remote: &OwnedFd, stream: &Stream, index: usize) -> Result<Screenshot> {
        let path = std::env::temp_dir().join(format!(
            "whispercat_screenshot_{}_{}.png",
            std::process::id(),
            uuid::Uuid::new_v4()
        ));
        let fd = remote
            .try_clone()
            .context("Could not duplicate the PipeWire screen-sharing handle")?
            .into_raw_fd();
        let node_id = stream.pipe_wire_node_id();
        let output = unsafe {
            let mut command = Command::new("gst-launch-1.0");
            command
                .args([
                    "-q",
                    "pipewiresrc",
                    &format!("fd={fd}"),
                    &format!("path={node_id}"),
                    "do-timestamp=true",
                    "!",
                    "videoconvert",
                    "!",
                    "pngenc",
                    "snapshot=true",
                    "!",
                    "filesink",
                    &format!("location={}", path.display()),
                ])
                .pre_exec(move || {
                    let flags = libc::fcntl(fd, libc::F_GETFD);
                    if flags < 0 || libc::fcntl(fd, libc::F_SETFD, flags & !libc::FD_CLOEXEC) < 0 {
                        return Err(std::io::Error::last_os_error());
                    }
                    Ok(())
                });
            command.output()
        }
        .context("Could not start GStreamer for PipeWire screenshot capture")?;

        // The child inherits and closes this descriptor. Close our duplicate even
        // when GStreamer fails before execing it.
        unsafe { libc::close(fd) };
        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
            let _ = fs::remove_file(&path);
            anyhow::bail!(
                "GStreamer could not capture the selected monitor. Install the GStreamer PipeWire and PNG plugins. {stderr}"
            );
        }

        let bytes = fs::read(&path).context("GStreamer did not produce a PNG screenshot")?;
        let _ = fs::remove_file(&path);
        Ok(Screenshot {
            filename: format!("monitor-{}.png", index + 1),
            bytes,
        })
    }
}

#[cfg(target_os = "linux")]
pub use platform::*;

#[cfg(not(target_os = "linux"))]
pub struct Screenshot {
    pub filename: String,
    pub bytes: Vec<u8>,
}

#[cfg(not(target_os = "linux"))]
pub struct ScreenshotSession;

#[cfg(not(target_os = "linux"))]
impl ScreenshotSession {
    pub async fn start() -> anyhow::Result<Self> {
        anyhow::bail!(
            "Screenshot workflow steps currently require Linux with a Wayland desktop portal."
        )
    }

    pub async fn capture(&self) -> anyhow::Result<Vec<Screenshot>> {
        anyhow::bail!(
            "Screenshot workflow steps currently require Linux with a Wayland desktop portal."
        )
    }

    pub async fn close(self) {}
}
