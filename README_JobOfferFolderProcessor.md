# Job Offer Folder Processor con Ollama LLM

Questa nuova classe legge email dalle cartelle Gmail dedicate alle job offers e usa **Ollama** (LLM locale) per estrarre automaticamente tutte le informazioni rilevanti.

## 🎯 Caratteristiche

✅ **Legge da cartelle Gmail specifiche**:
- JobOffers_CadreEmploi
- JobOffers_APEC
- JobOffers_MichaelPage
- JobOffers_Linkedin
- JobOffers_WTTJ

✅ **Estrazione intelligente con LLM locale** (Ollama):
- Informazioni azienda e posizione
- URL per candidarsi
- Scadenze e istruzioni per application
- Skills richieste
- Salario (se menzionato)
- Contatti

✅ **Campo source_folder** per tracciare l'origine:
```json
"source_folder": "JobOffers_APEC"
```

✅ **Gestione multiple posizioni**:
- Rileva se l'email contiene più offerte
- Estrae lista completa delle posizioni

✅ **Salvataggio incrementale**:
- Salva dopo ogni estrazione
- Resume automatico se interrotto

## 📋 Prerequisiti

### 1. Installare Ollama

```bash
# macOS / Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows
# Download from https://ollama.com/download
```

### 2. Scaricare un modello LLM

```bash
# Modello consigliato (veloce e accurato)
ollama pull llama3.2:latest

# Alternative:
ollama pull mistral:latest
ollama pull gemma2:latest
```

### 3. Avviare Ollama

```bash
ollama serve
```

Verifica che sia attivo su `http://localhost:11434`

## 🚀 Utilizzo

### Compilare il progetto

```bash
mvn clean compile
```

### Eseguire il processor

```bash
mvn exec:java -Dexec.mainClass="com.agty.version_fetch_folders.JobAlertEmailFoldersProcessor"
```

### Output

Il programma genera il file:
```
tools_data/job_offers_extracted.json
```

## 📊 Formato Output

Ogni email viene estratta in questo formato:

```json
{
  "from": "recruiter@company.com",
  "subject": "Senior Java Developer Position",
  "sentDate": 1733400000000,
  "source_folder": "JobOffers_Linkedin",

  "company": "TechCorp SAS",
  "position_title": "Senior Java Developer",
  "location": "Paris / Remote",
  "contract_type": "CDI",
  "salary_range": "60000-80000 EUR",

  "description": "We are looking for an experienced Java developer...",
  "required_skills": ["Java", "Spring Boot", "Kubernetes", "AWS"],
  "experience_level": "Senior",

  "application_url": "https://careers.techcorp.com/apply/12345",
  "application_email": "jobs@techcorp.com",
  "application_instructions": "Send CV and cover letter",
  "application_deadline": "2025-01-15",

  "is_multiple_positions": false,
  "number_of_positions": 1,
  "positions_list": null,

  "contact_person": "Marie Dubois",
  "contact_phone": "+33 1 23 45 67 89",
  "reference_number": "REF-2025-001",

  "extraction_timestamp": 1733400123456,
  "extraction_confidence": 0.92,
  "email_content_preview": "Dear candidate, we are excited to..."
}
```

## ⚙️ Configurazione

### Cambiare il modello LLM

Modifica in `JobOfferFolderProcessor.java`:

```java
String ollamaModel = "mistral:latest";  // Invece di llama3.2
```

### Cambiare l'host Ollama

Se Ollama è su un server remoto:

```java
String ollamaHost = "http://192.168.1.100:11434";
```

### Modificare le cartelle

Modifica l'array `JOB_FOLDERS`:

```java
private static final String[] JOB_FOLDERS = {
    "JobOffers_CadreEmploi",
    "JobOffers_APEC",
    "TuaNuovaCartella"
};
```

### Cambiare il periodo (default 7 giorni)

Nella classe `JobOfferFolderProcessor.java`, alla linea ~34:

```java
private static final int DAYS_TO_SEARCH = 7;  // Cambia a 14 per 2 settimane, 30 per un mese, etc.
```

## 🔧 Modelli Ollama Consigliati

| Modello | Dimensione | Velocità | Accuratezza | Uso |
|---------|-----------|----------|-------------|-----|
| **llama3.2:latest** | 2GB | ⚡⚡⚡ | ⭐⭐⭐⭐ | **Consigliato** |
| mistral:latest | 4GB | ⚡⚡ | ⭐⭐⭐⭐⭐ | Più accurato |
| gemma2:9b | 5GB | ⚡⚡ | ⭐⭐⭐⭐ | Bilanciato |
| codellama:latest | 4GB | ⚡⚡ | ⭐⭐⭐ | Per tech jobs |

## 🐛 Troubleshooting

### "Cannot connect to Ollama"

```bash
# Verifica che Ollama sia in esecuzione
curl http://localhost:11434/api/tags

# Se non risponde, avvia Ollama
ollama serve
```

### "Model not found"

Il programma **scarica automaticamente** il modello se non è presente. La prima volta potrebbe richiedere alcuni minuti.

Se vuoi scaricare manualmente:
```bash
ollama pull llama3.2:latest
```

### "Folder does not exist"

Le cartelle devono esistere in Gmail. Creale manualmente se necessario:
- Vai su Gmail Web
- Crea le cartelle (label): JobOffers_APEC, etc.
- Sposta le email nelle cartelle appropriate

### Errori di parsing JSON

Se l'LLM non restituisce JSON valido, prova:
1. Un modello più grande (mistral invece di llama3.2)
2. Modificare il prompt per essere più esplicito
3. Aumentare la temperatura del modello

## 📝 Note

- Il processor **salta automaticamente** le email già processate
- Puoi **interrompere** e riprendere l'esecuzione - riprenderà da dove si era fermato
- Il **salvataggio è incrementale** - ogni email estratta viene salvata immediatamente
- Il campo **extraction_confidence** indica quanto l'LLM è sicuro dell'estrazione (0.0-1.0)

## 🔄 Confronto con JobOpportunityExtractor

| Feature | JobOpportunityExtractor | JobOfferFolderProcessor |
|---------|------------------------|-------------------------|
| LLM | OpenAI (cloud, a pagamento) | Ollama (locale, gratis) |
| Input | job_opportunities_emails.json | Cartelle Gmail |
| Source tracking | ❌ | ✅ source_folder field |
| Multiple positions | Limitato | ✅ Completo |
| Application URLs | Singolo | ✅ Tutti i link estratti |
| Costi | $$ per API calls | Gratis (usa GPU locale) |

## 🎓 Best Practices

1. **Organizza le email in cartelle Gmail** prima di eseguire
2. **Usa regole Gmail** per spostare automaticamente le email nelle cartelle giuste
3. **Esegui periodicamente** (es. ogni lunedì mattina) per tenere aggiornato il database
4. **Verifica manualmente** le prime estrazioni per validare la qualità
5. **Regola il prompt** se necessario per il tuo caso d'uso specifico

## 🚀 Prossimi Sviluppi

- [ ] Supporto per immagini nelle email (CV allegati)
- [ ] Deduplicazione intelligente di posizioni duplicate
- [ ] Scoring automatico di fit con il tuo profilo
- [ ] Notifiche per nuove posizioni rilevanti
- [ ] Export in altri formati (CSV, Excel)
