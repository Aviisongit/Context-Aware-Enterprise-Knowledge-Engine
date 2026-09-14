import { useEffect, useMemo, useState } from 'react'
import './App.css'
import { fetchJob, queryKnowledge, subscribeToGlobalTelemetry, subscribeToJob, uploadDocument } from './services/api'

const tabs = ['Chat', 'Documents', 'Ingestion Telemetry', 'Source Citations']

const starterDocuments = [
  {
    id: 1,
    filename: 'sample-kb.md',
    source: 'MARKDOWN',
    syncStatus: 'SYNCED',
    createdAt: '2026-09-13T15:00:00Z',
  },
  {
    id: 2,
    filename: 'ops-manual.pdf',
    source: 'PDF',
    syncStatus: 'PENDING',
    createdAt: '2026-09-13T14:30:00Z',
  },
]

function App() {
  const [activeTab, setActiveTab] = useState('Chat')
  const [question, setQuestion] = useState('What does the sample knowledge base say about operations?')
  const [answer, setAnswer] = useState('Ask a question to generate a grounded answer from the indexed documents.')
  const [citations, setCitations] = useState([
    {
      documentFilename: 'sample-kb.md',
      chunkIndex: 0,
      sectionName: 'Overview',
      snippet: 'The customer operations team manages requests, escalations, and approvals through the shared knowledge base.',
    },
  ])
  const [selectedCitation, setSelectedCitation] = useState(citations[0])
  const [isQueryLoading, setIsQueryLoading] = useState(false)
  const [documents, setDocuments] = useState(starterDocuments)
  const [selectedFile, setSelectedFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [uploadMessage, setUploadMessage] = useState('')
  const [jobs, setJobs] = useState([])
  const [jobEvents, setJobEvents] = useState([])

  useEffect(() => {
    const client = subscribeToGlobalTelemetry((event) => {
      setJobEvents((previous) => [event, ...previous].slice(0, 10))
      setJobs((previous) => {
        const existingIndex = previous.findIndex((job) => job.jobId === event.jobId)
        if (existingIndex >= 0) {
          const next = [...previous]
          next[existingIndex] = {
            ...next[existingIndex],
            ...event,
            status: event.status,
            stage: event.stage,
          }
          return next
        }

        return [
          {
            jobId: event.jobId,
            documentId: event.documentId,
            filename: `document-${event.documentId ?? 'unknown'}`,
            status: event.status,
            stage: event.stage,
          },
          ...previous,
        ]
      })
    })

    return () => client.deactivate()
  }, [])

  const activeJobs = useMemo(() => jobs.slice(0, 6), [jobs])

  function handleCitationClick(citation) {
    setSelectedCitation(citation)
    setActiveTab('Source Citations')
  }

  async function handleUpload(event) {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }

    setSelectedFile(file.name)
    setUploading(true)
    setUploadMessage('')

    try {
      const result = await uploadDocument(file)
      const jobId = result.jobId
      const documentId = result.documentId

      setUploadMessage(`Upload accepted. Job ${jobId} is processing.`)
      setDocuments((previous) => [
        {
          id: documentId,
          filename: file.name,
          source: file.name.endsWith('.pdf') ? 'PDF' : 'MARKDOWN',
          syncStatus: 'PENDING',
          createdAt: new Date().toISOString(),
        },
        ...previous,
      ])

      setJobs((previous) => [
        {
          jobId,
          documentId,
          filename: file.name,
          status: 'PENDING',
          stage: 'Queued',
        },
        ...previous,
      ])

      const socketClient = subscribeToJob(jobId, (message) => {
        setJobEvents((previous) => [message, ...previous].slice(0, 10))
        setJobs((previousJobs) =>
          previousJobs.map((job) =>
            job.jobId === jobId ? { ...job, ...message, status: message.status, stage: message.stage } : job,
          ),
        )

        if (message.status === 'COMPLETE' || message.status === 'FAILED') {
          fetchJob(jobId)
            .then((jobDetails) => {
              setJobs((previousJobs) =>
                previousJobs.map((job) =>
                  job.jobId === jobId ? { ...job, ...jobDetails, status: jobDetails.status } : job,
                ),
              )
            })
            .catch((error) => console.error('Failed to fetch job details', error))
            .finally(() => socketClient.deactivate())
        }
      })
    } catch (error) {
      setUploadMessage(error.message || 'Document upload failed')
    } finally {
      setUploading(false)
      event.target.value = ''
    }
  }

  async function handleQuerySubmit(event) {
    event.preventDefault()
    if (!question.trim()) {
      return
    }

    setIsQueryLoading(true)
    try {
      const response = await queryKnowledge(question)
      setAnswer(response.answer || 'No answer returned.')
      setCitations(response.citations || [])
      setSelectedCitation((response.citations && response.citations[0]) || null)
    } catch (error) {
      setAnswer(error.message || 'Failed to generate an answer.')
      setCitations([])
      setSelectedCitation(null)
    } finally {
      setIsQueryLoading(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Context-Aware Enterprise Knowledge Engine</p>
          <h1>Operations Console</h1>
        </div>
        <button type="button" className="primary-button" onClick={() => setActiveTab('Chat')}>
          New inquiry
        </button>
      </header>

      <nav className="view-tabs" aria-label="Primary navigation">
        {tabs.map((tab) => (
          <button
            key={tab}
            type="button"
            className={`tab-button ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {tab}
          </button>
        ))}
      </nav>

      {activeTab === 'Chat' && (
        <section className="workspace-panel">
          <div className="left-column">
            <div className="panel">
              <div className="panel-header">
                <span className="badge">Query</span>
              </div>
              <h2>Ask the knowledge engine</h2>
              <form className="query-form" onSubmit={handleQuerySubmit}>
                <textarea
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  rows={6}
                  placeholder="Ask a question about your knowledge base..."
                />
                <div className="form-actions">
                  <button className="primary-button" type="submit" disabled={isQueryLoading}>
                    {isQueryLoading ? 'Generating...' : 'Generate answer'}
                  </button>
                </div>
              </form>
            </div>
          </div>

          <div className="right-column">
            <div className="panel answer-card">
              <div className="panel-header">
                <span className="badge">Answer</span>
              </div>
              <h2>Grounded response</h2>
              <p>{answer}</p>
            </div>

            <div className="panel citation-card">
              <div className="panel-header">
                <span className="badge">Citations</span>
              </div>
              <h2>Sources</h2>
              {citations.length === 0 ? (
                <p>No sources yet.</p>
              ) : (
                <div className="citation-badge-list">
                  {citations.map((citation, index) => (
                    <button
                      key={`${citation.documentFilename}-${citation.chunkIndex}-${index}`}
                      type="button"
                      className="citation-badge"
                      onClick={() => handleCitationClick(citation)}
                    >
                      [{index + 1}] {citation.documentFilename}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        </section>
      )}

      {activeTab === 'Documents' && (
        <section className="panel-grid two-columns">
          <div className="panel">
            <div className="panel-header">
              <span className="badge">Upload</span>
            </div>
            <h2>Ingest a document</h2>
            <label className="file-picker">
              <input type="file" onChange={handleUpload} />
              <span>{selectedFile ? `Selected: ${selectedFile}` : 'Choose Markdown or PDF file'}</span>
            </label>
            {uploading && <p className="status-note">Uploading...</p>}
            {uploadMessage && <p className="status-note success">{uploadMessage}</p>}
          </div>

          <div className="panel">
            <div className="panel-header">
              <span className="badge">Documents</span>
            </div>
            <h2>Indexed knowledge</h2>
            <ul className="doc-list">
              {documents.map((document) => (
                <li key={document.id} className="doc-item">
                  <div>
                    <strong>{document.filename}</strong>
                    <small>{document.source}</small>
                  </div>
                  <span className={`pill ${document.syncStatus.toLowerCase()}`}>{document.syncStatus}</span>
                  <time>{new Date(document.createdAt).toLocaleString()}</time>
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      {activeTab === 'Ingestion Telemetry' && (
        <section className="panel-grid two-columns">
          <div className="panel">
            <div className="panel-header">
              <span className="badge">Jobs</span>
            </div>
            <h2>Queue and stage progress</h2>
            {activeJobs.length === 0 ? (
              <p>No active jobs.</p>
            ) : (
              <ul className="job-list">
                {activeJobs.map((job) => (
                  <li key={job.jobId} className="job-item">
                    <div>
                      <strong>{job.filename || `job-${job.jobId}`}</strong>
                      <small>Job {job.jobId}</small>
                    </div>
                    <span className={`pill ${String(job.status || 'PENDING').toLowerCase()}`}>
                      {job.status || 'PENDING'}
                    </span>
                    <em>{job.stage || 'Queued'}</em>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="panel">
            <div className="panel-header">
              <span className="badge">Telemetry</span>
            </div>
            <h2>Live event log</h2>
            <ul className="event-list">
              {jobEvents.length === 0 ? (
                <li>No telemetry yet.</li>
              ) : (
                jobEvents.map((event, index) => (
                  <li key={`${event.jobId}-${event.timestamp || index}`}>
                    <strong>{event.status}</strong>
                    <span>{event.stage}</span>
                    <small>{event.timestamp || 'just now'}</small>
                  </li>
                ))
              )}
            </ul>
          </div>
        </section>
      )}

      {activeTab === 'Source Citations' && (
        <section className="panel-grid two-columns">
          <div className="panel">
            <div className="panel-header">
              <span className="badge">Evidence</span>
            </div>
            <h2>Retrieved chunks</h2>
            {citations.length === 0 ? (
              <p>No citation data available.</p>
            ) : (
              <ul className="citation-detail-list">
                {citations.map((citation, index) => (
                  <li key={`${citation.documentFilename}-${citation.chunkIndex}-${index}`}>
                    <button type="button" className="citation-row" onClick={() => handleCitationClick(citation)}>
                      <strong>{citation.documentFilename}</strong>
                      <span>Section: {citation.sectionName}</span>
                      <small>Chunk index: {citation.chunkIndex}</small>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="panel">
            <div className="panel-header">
              <span className="badge">Metadata</span>
            </div>
            <h2>Inspect selected chunk</h2>
            {selectedCitation ? (
              <div className="citation-inspector">
                <p className="meta-line">
                  <strong>{selectedCitation.documentFilename}</strong> · Section: {selectedCitation.sectionName} · Chunk {selectedCitation.chunkIndex}
                </p>
                <blockquote>{selectedCitation.snippet || 'No snippet available.'}</blockquote>
              </div>
            ) : (
              <p>Select a citation to inspect its exact text snippet.</p>
            )}
          </div>
        </section>
      )}
    </div>
  )
}

export default App
