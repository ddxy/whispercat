use crate::{
    config,
    postprocess::{self, PostProcessing},
};
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RunStatus {
    Processing,
    Done,
    Failed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RunStepStatus {
    Pending,
    Processing,
    Done,
    Failed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RunStep {
    pub path: Vec<usize>,
    pub label: String,
    pub status: RunStepStatus,
    #[serde(default)]
    pub output: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Run {
    pub id: String,
    pub created_at: u64,
    pub status: RunStatus,
    pub workflow_uuid: Option<String>,
    pub workflow_title: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub recording_dir: Option<PathBuf>,
    #[serde(default)]
    pub transcript: String,
    #[serde(default)]
    pub result: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
    #[serde(default)]
    pub steps: Vec<RunStep>,
}

#[derive(Clone)]
pub struct RunStore {
    runs: Arc<Mutex<Vec<Run>>>,
}

impl RunStore {
    pub fn load() -> Self {
        let mut runs: Vec<Run> = std::fs::read_to_string(path())
            .ok()
            .and_then(|raw| serde_json::from_str(&raw).ok())
            .unwrap_or_default();

        let mut changed = false;
        for run in &mut runs {
            if matches!(run.status, RunStatus::Processing) {
                run.status = RunStatus::Failed;
                run.error = Some("WhisperCat was closed before processing finished.".to_string());
                changed = true;
            }
        }
        if changed {
            if let Err(error) = save(&runs) {
                tracing::warn!("Could not update interrupted history runs: {error}");
            }
        }

        Self {
            runs: Arc::new(Mutex::new(runs)),
        }
    }

    pub fn list(&self) -> Vec<Run> {
        self.runs
            .lock()
            .map(|runs| runs.clone())
            .unwrap_or_else(|poisoned| poisoned.into_inner().clone())
    }

    pub fn create(
        &self,
        workflow: Option<&PostProcessing>,
        transcript: String,
        recording_dir: Option<PathBuf>,
    ) -> Result<Run> {
        let run = Run {
            id: uuid::Uuid::new_v4().to_string(),
            created_at: SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u64,
            status: RunStatus::Processing,
            workflow_uuid: workflow.map(|workflow| workflow.uuid.clone()),
            workflow_title: workflow
                .map(|workflow| workflow.title.clone())
                .unwrap_or_else(|| "No workflow".to_string()),
            recording_dir,
            transcript,
            result: String::new(),
            error: None,
            steps: workflow
                .map(|workflow| postprocess::workflow_steps(&workflow.steps))
                .unwrap_or_default(),
        };
        self.update(|runs| {
            runs.insert(0, run.clone());
        })?;
        Ok(run)
    }

    pub fn set_transcript(&self, id: &str, transcript: String) -> Result<()> {
        self.update(|runs| {
            if let Some(run) = runs.iter_mut().find(|run| run.id == id) {
                run.transcript = transcript;
            }
        })
    }

    pub fn complete(&self, id: &str, result: String) -> Result<()> {
        self.update(|runs| {
            if let Some(run) = runs.iter_mut().find(|run| run.id == id) {
                run.status = RunStatus::Done;
                run.result = result;
                run.error = None;
            }
        })
    }

    pub fn start_step(&self, id: &str, path: &[usize]) -> Result<()> {
        self.update(|runs| {
            if let Some(step) = runs
                .iter_mut()
                .find(|run| run.id == id)
                .and_then(|run| run.steps.iter_mut().find(|step| step.path == path))
            {
                step.status = RunStepStatus::Processing;
                step.error = None;
            }
        })
    }

    pub fn complete_step(&self, id: &str, path: &[usize], output: String) -> Result<()> {
        self.update(|runs| {
            if let Some(step) = runs
                .iter_mut()
                .find(|run| run.id == id)
                .and_then(|run| run.steps.iter_mut().find(|step| step.path == path))
            {
                step.status = RunStepStatus::Done;
                step.output = output;
                step.error = None;
            }
        })
    }

    pub fn fail(&self, id: &str, error: String) -> Result<()> {
        self.update(|runs| {
            if let Some(run) = runs.iter_mut().find(|run| run.id == id) {
                run.status = RunStatus::Failed;
                run.error = Some(error);
                if let Some(step) = run
                    .steps
                    .iter_mut()
                    .find(|step| matches!(step.status, RunStepStatus::Processing))
                {
                    step.status = RunStepStatus::Failed;
                    step.error = run.error.clone();
                }
            }
        })
    }

    pub fn clear(&self) -> Result<()> {
        let recording_dirs = self
            .list()
            .into_iter()
            .filter_map(|run| run.recording_dir)
            .collect::<Vec<_>>();
        self.update(|runs| runs.clear())?;
        for directory in recording_dirs {
            if is_recording_dir(&directory) {
                if let Err(error) = std::fs::remove_dir_all(&directory) {
                    tracing::warn!("Could not remove recording directory {}: {error}", directory.display());
                }
            }
        }
        Ok(())
    }

    pub fn open_recording_dir(&self, id: &str) -> Result<()> {
        let directory = self
            .runs
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .iter()
            .find(|run| run.id == id)
            .and_then(|run| run.recording_dir.clone())
            .ok_or_else(|| anyhow!("This history entry has no recording folder."))?;
        if !is_recording_dir(&directory) {
            return Err(anyhow!("Recording folder is no longer available."));
        }

        let mut command = open_command();
        command.arg(directory);
        command.spawn()?;
        Ok(())
    }

    fn update(&self, change: impl FnOnce(&mut Vec<Run>)) -> Result<()> {
        let mut runs = self
            .runs
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        change(&mut runs);
        save(&runs)
    }
}

fn is_recording_dir(directory: &Path) -> bool {
    let Ok(root) = crate::recorder::recording_root().canonicalize() else {
        return false;
    };
    let Ok(directory) = directory.canonicalize() else {
        return false;
    };
    directory.parent() == Some(root.as_path())
}

#[cfg(target_os = "linux")]
fn open_command() -> Command {
    Command::new("xdg-open")
}

#[cfg(target_os = "macos")]
fn open_command() -> Command {
    Command::new("open")
}

#[cfg(target_os = "windows")]
fn open_command() -> Command {
    Command::new("explorer")
}

fn path() -> std::path::PathBuf {
    config::app_dir().join("history.json")
}

fn save(runs: &[Run]) -> Result<()> {
    std::fs::write(path(), serde_json::to_string_pretty(runs)?)?;
    Ok(())
}
