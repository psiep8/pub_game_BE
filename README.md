# PubGame BE — Backend Spring Boot

Backend Spring Boot 3.x per il gioco da pub multigiocatore.
Gestisce REST API, WebSocket (STOMP/SockJS), generazione domande AI via Groq, e integrazione con TMDB e iTunes.

## Requisiti

- JDK 21+
- Maven 3.8+ (incluso `./mvnw`)
- MySQL 8+ (database `pub_game`)

## Configurazione

Copia o imposta le variabili d'ambiente:

```bash
export GROQ_API_KEY="gsk_..."
export TMDB_API_KEY="..."
```

Oppure modifica `application.yml`:

```yaml
groq:
  api:
    key: ${GROQ_API_KEY}
    model: "llama-3.3-70b-versatile"    # Modello AI
    temperature: 0.7                     # Creatività default
    image-blur-temperature: 1.3          # Creatività per IMAGE_BLUR

tmdb:
  api:
    key: ${TMDB_API_KEY}

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pub_game?createDatabaseIfNotExist=true
    username: root
    password: root
```

## Avvio

```bash
# Compila
./mvnw compile

# Avvia
./mvnw spring-boot:run

# Oppure build e run
./mvnw package -DskipTests
java -jar target/pub_game_BE-*.jar
```

Il server parte su `http://0.0.0.0:8080`.

## API principali

| Metodo | Endpoint | Descrizione |
|---|---|---|
| `POST` | `/games` | Crea una nuova partita |
| `GET` | `/games` | Elenca tutte le partite |
| `GET` | `/games/{id}/current-round` | Round corrente |
| `GET` | `/games/{id}/generate-ai-round?category=X&type=Y&difficulty=Z` | Genera round AI via Groq |
| `POST` | `/games/{id}/round` | Round da database (fallback free) |
| `GET` | `/categories` | Elenca categorie |
| `GET` | `/questions` | Elenca domande |

## WebSocket (STOMP)

| Endpoint | Descrizione |
|---|---|
| `ws-pubgame` | Endpoint SockJS |
| `/app/game/{id}/answer` | Invia risposta giocatore |
| `/app/game/{id}/status` | Aggiornamenti stato gioco |
| `/app/scream` | Scream Race (volume microfono) |
| `/app/scream/reset` | Reset gara |

Broadcast su `/topic/game/{id}`, `/topic/game/{id}/responses`, `/topic/game/{id}/status`.

## Game Types supportati

| Tipo | Descrizione | AI |
|---|---|---|
| `QUIZ` | 4 opzioni, una corretta | Groq |
| `TRUE_FALSE` | Vero/Falso con spiegazione | Groq |
| `CHRONO` | Indovina l'anno storico | Groq |
| `IMAGE_BLUR` | Riconosci il celebrity | Groq + TMDB |
| `ONE_VS_ONE` | Sfida 1vs1 | Groq |
| `MUSIC` | Indovina la canzone | iTunes API |
| `ROULETTE` | Scegli un colore | Random |
| `WHEEL_OF_FORTUNE` | Proverbio casuale | Lista locale |
| `SCREAM_RACE` | Gara di urla | Microfono |

Se l'AI non risponde o il JSON non è valido, viene usato un fallback locale (funziona anche senza chiave Groq).

## Struttura

```
src/main/java/com/pub_game_be/
├── config/          # WebSocket, RestTemplate (+ CORS rimosso, gestito da SecurityConfig)
├── controller/      # REST + WebSocket controllers
├── domain/          # Entity JPA (Game, GameRound, Question, Category, QuestionOption)
│   ├── category/
│   ├── enums/       # GameStatus, GameType, QuestionType, RoundStatus
│   ├── game/
│   ├── game_round/
│   └── question/
├── dto/             # MusicTrackDto, ScreamDto, AppleMusic DTOs
├── repository/      # JPA repositories
├── security/        # SecurityConfig (permitAll + CORS)
└── service/         # QuestionGenerator (AI), TMDBImage, AppleMusic, Game, SimpleRound
```
