use crate::{config, postprocess::PostProcessing};
use anyhow::Result;
use serde::{Deserialize, Serialize};
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
pub struct Run {
    pub id: String,
    pub created_at: u64,
    pub status: RunStatus,
    pub workflow_uuid: Option<String>,
    pub workflow_title: String,
    #[serde(default)]
    pub transcript: String,
    #[serde(default)]
    pub result: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
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

    pub fn create(&self, workflow: Option<&PostProcessing>, transcript: String) -> Result<Run> {
        let run = Run {
            id: uuid::Uuid::new_v4().to_string(),
            created_at: SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis() as u64,
            status: RunStatus::Processing,
            workflow_uuid: workflow.map(|workflow| workflow.uuid.clone()),
            workflow_title: workflow
                .map(|workflow| workflow.title.clone())
                .unwrap_or_else(|| "No workflow".to_string()),
            transcript,
            result: String::new(),
            error: None,
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

    pub fn fail(&self, id: &str, error: String) -> Result<()> {
        self.update(|runs| {
            if let Some(run) = runs.iter_mut().find(|run| run.id == id) {
                run.status = RunStatus::Failed;
                run.error = Some(error);
            }
        })
    }

    pub fn clear(&self) -> Result<()> {
        self.update(|runs| runs.clear())
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

fn path() -> std::path::PathBuf {
    config::app_dir().join("history.json")
}

fn save(runs: &[Run]) -> Result<()> {
    std::fs::write(path(), serde_json::to_string_pretty(runs)?)?;
    Ok(())
}
