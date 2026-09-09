<?php
/**
 * MyJournal+ AI proxy — put at: public_html/api/ai-chat.php
 * App calls: POST https://app.myjournalplus.com/api/ai-chat
 * Config: copy config.sample.php → config.php
 */
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Authorization, Content-Type');
header('Access-Control-Allow-Methods: POST, OPTIONS');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405); echo json_encode(['error' => 'POST only']); exit;
}

$configFile = __DIR__ . '/config.php';
if (!is_file($configFile)) {
    http_response_code(500); echo json_encode(['error' => 'Server config.php missing']); exit;
}
$cfg = require $configFile;
$GEMINI_KEY = $cfg['GEMINI_API_KEY'] ?? '';
$PROJECT_ID = $cfg['FIREBASE_PROJECT_ID'] ?? 'myjournal-plus';
$MODEL = $cfg['GEMINI_MODEL'] ?? 'gemini-2.0-flash';

if ($GEMINI_KEY === '' || strpos($GEMINI_KEY, 'YOUR_') === 0 || strpos($GEMINI_KEY, 'AIza...') === 0) {
    http_response_code(500); echo json_encode(['error' => 'GEMINI_API_KEY not configured on server']); exit;
}

$authHeader = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
if (!preg_match('/Bearer\s+(\S+)/i', $authHeader, $m)) {
    http_response_code(401); echo json_encode(['error' => 'Missing Authorization Bearer token']); exit;
}
$idToken = $m[1];
$uid = verify_firebase_id_token($idToken, $PROJECT_ID);
if (!$uid) {
    http_response_code(401); echo json_encode(['error' => 'Invalid or expired token']); exit;
}

$input = json_decode(file_get_contents('php://input'), true) ?: [];
$question = trim((string)($input['question'] ?? ''));
$chatId = trim((string)($input['chatId'] ?? ''));
if ($question === '' || mb_strlen($question) > 2000) {
    http_response_code(400); echo json_encode(['error' => 'question required (max 2000 chars)']); exit;
}

$user = firestore_get($PROJECT_ID, $idToken, "users/$uid");
$isPremium = !empty($user['isPremium']);
$freeUsed = intval($user['aiFreeUsedCount'] ?? 0) >= 1;
if (!$isPremium && $freeUsed) {
    http_response_code(402);
    echo json_encode(['error' => 'FREE_LIMIT_REACHED', 'code' => 'FREE_LIMIT_REACHED']);
    exit;
}

$entries = firestore_list($PROJECT_ID, $idToken, "users/$uid/entries", 8);
$moods = firestore_list($PROJECT_ID, $idToken, "users/$uid/moods", 10);
$entriesCtx = '';
foreach ($entries as $e) {
    $title = $e['title'] ?? 'Untitled';
    $content = mb_substr((string)($e['content'] ?? ''), 0, 220);
    $entriesCtx .= "- $title: $content\n";
}
$moodsCtx = '';
foreach ($moods as $mo) {
    $moodsCtx .= '- ' . ($mo['mood'] ?? '?') . ' (' . ($mo['score'] ?? '?') . "/5)\n";
}

$prompt = "You are a warm, supportive journaling companion for MyJournal+.\n"
    . "Help the user reflect on their journal and moods. Be concise (under 220 words). "
    . "No medical diagnoses. If severe distress, gently suggest professional help.\n\n"
    . "Entries:\n" . ($entriesCtx ?: "(none yet)\n") . "\n"
    . "Moods:\n" . ($moodsCtx ?: "(none yet)\n") . "\n"
    . "User question:\n$question";

$geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/{$MODEL}:generateContent?key=" . urlencode($GEMINI_KEY);
$payload = json_encode([
    'contents' => [['role' => 'user', 'parts' => [['text' => $prompt]]]],
    'generationConfig' => ['temperature' => 0.7, 'maxOutputTokens' => 512],
]);
$g = curl_json($geminiUrl, $payload);
if (!$g['ok']) {
    http_response_code(502);
    echo json_encode(['error' => 'AI error: ' . substr($g['body'], 0, 180)]);
    exit;
}
$gjson = json_decode($g['body'], true);
$answer = '';
foreach ($gjson['candidates'][0]['content']['parts'] ?? [] as $part) {
    $answer .= $part['text'] ?? '';
}
if ($answer === '') $answer = 'I could not generate a response. Please try again.';

if ($chatId === '') {
    $chatId = firestore_create($PROJECT_ID, $idToken, "users/$uid/aiChats", [
        'title' => mb_substr($question, 0, 60),
        'createdAt' => firestore_timestamp_now(),
        'updatedAt' => firestore_timestamp_now(),
    ]) ?: '';
}
if ($chatId) {
    firestore_create($PROJECT_ID, $idToken, "users/$uid/aiChats/$chatId/messages", [
        'role' => 'user', 'text' => $question, 'createdAt' => firestore_timestamp_now(),
    ]);
    firestore_create($PROJECT_ID, $idToken, "users/$uid/aiChats/$chatId/messages", [
        'role' => 'assistant', 'text' => $answer, 'createdAt' => firestore_timestamp_now(),
    ]);
    firestore_patch($PROJECT_ID, $idToken, "users/$uid/aiChats/$chatId", [
        'updatedAt' => firestore_timestamp_now(),
        'title' => mb_substr($question, 0, 60),
    ]);
}
if (!$isPremium) {
    $newCount = intval($user['aiFreeUsedCount'] ?? 0) + 1;
    firestore_patch($PROJECT_ID, $idToken, "users/$uid", ['aiFreeUsedCount' => $newCount]);
}

echo json_encode([
    'answer' => $answer,
    'chatId' => $chatId,
    'freeLimitReached' => !$isPremium,
    'isPremium' => $isPremium,
]);

function verify_firebase_id_token(string $token, string $projectId): ?string {
    $parts = explode('.', $token);
    if (count($parts) !== 3) return null;
    $payload = json_decode(base64_url_decode($parts[1]), true);
    if (!$payload) return null;
    if (($payload['aud'] ?? '') !== $projectId) return null;
    if (($payload['iss'] ?? '') !== "https://securetoken.google.com/$projectId") return null;
    if (($payload['exp'] ?? 0) < time()) return null;
    $uid = $payload['user_id'] ?? $payload['sub'] ?? null;
    $header = json_decode(base64_url_decode($parts[0]), true);
    $kid = $header['kid'] ?? '';
    $certs = @file_get_contents('https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');
    $certMap = $certs ? json_decode($certs, true) : null;
    if (!$certMap || empty($certMap[$kid])) return null;
    $pub = openssl_pkey_get_public($certMap[$kid]);
    if (!$pub) return null;
    $ok = openssl_verify($parts[0] . '.' . $parts[1], base64_url_decode($parts[2]), $pub, OPENSSL_ALGO_SHA256);
    return $ok === 1 ? $uid : null;
}
function base64_url_decode(string $data): string {
    $remainder = strlen($data) % 4;
    if ($remainder) $data .= str_repeat('=', 4 - $remainder);
    return base64_decode(strtr($data, '-_', '+/')) ?: '';
}
function curl_json(string $url, ?string $body = null, array $headers = [], string $method = 'GET'): array {
    $ch = curl_init($url);
    $h = array_merge(['Content-Type: application/json'], $headers);
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_HTTPHEADER => $h, CURLOPT_TIMEOUT => 60]);
    if ($body !== null) {
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        if ($method !== 'POST') curl_setopt($ch, CURLOPT_CUSTOMREQUEST, $method);
    }
    $resp = curl_exec($ch);
    $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    return ['ok' => $code >= 200 && $code < 300, 'code' => $code, 'body' => (string)$resp];
}
function firestore_get(string $projectId, string $idToken, string $path): array {
    $url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$path";
    $r = curl_json($url, null, ["Authorization: Bearer $idToken"]);
    if (!$r['ok']) return [];
    return firestore_fields_to_array(json_decode($r['body'], true)['fields'] ?? []);
}
function firestore_list(string $projectId, string $idToken, string $path, int $limit): array {
    $url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$path?pageSize=$limit";
    $r = curl_json($url, null, ["Authorization: Bearer $idToken"]);
    if (!$r['ok']) return [];
    $docs = json_decode($r['body'], true)['documents'] ?? [];
    $out = [];
    foreach ($docs as $d) $out[] = firestore_fields_to_array($d['fields'] ?? []);
    return $out;
}
function firestore_create(string $projectId, string $idToken, string $path, array $data): ?string {
    $url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$path";
    $body = json_encode(['fields' => array_to_firestore_fields($data)]);
    $r = curl_json($url, $body, ["Authorization: Bearer $idToken"], 'POST');
    if (!$r['ok']) return null;
    $name = json_decode($r['body'], true)['name'] ?? '';
    $parts = explode('/', $name);
    return end($parts) ?: null;
}
function firestore_patch(string $projectId, string $idToken, string $path, array $data): void {
    $fields = array_keys($data);
    $mask = implode('&', array_map(function ($f) { return 'updateMask.fieldPaths=' . urlencode($f); }, $fields));
    $url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$path?$mask";
    $body = json_encode(['fields' => array_to_firestore_fields($data)]);
    curl_json($url, $body, ["Authorization: Bearer $idToken"], 'PATCH');
}
function firestore_fields_to_array(array $fields): array {
    $out = [];
    foreach ($fields as $k => $v) {
        if (isset($v['stringValue'])) $out[$k] = $v['stringValue'];
        elseif (isset($v['integerValue'])) $out[$k] = intval($v['integerValue']);
        elseif (isset($v['booleanValue'])) $out[$k] = (bool)$v['booleanValue'];
        elseif (isset($v['doubleValue'])) $out[$k] = $v['doubleValue'];
        else $out[$k] = null;
    }
    return $out;
}
function array_to_firestore_fields(array $data): array {
    $fields = [];
    foreach ($data as $k => $v) {
        if (is_bool($v)) $fields[$k] = ['booleanValue' => $v];
        elseif (is_int($v)) $fields[$k] = ['integerValue' => (string)$v];
        elseif (is_float($v)) $fields[$k] = ['doubleValue' => $v];
        elseif (is_array($v) && isset($v['timestampValue'])) $fields[$k] = $v;
        else $fields[$k] = ['stringValue' => (string)$v];
    }
    return $fields;
}
function firestore_timestamp_now(): array {
    return ['timestampValue' => gmdate('Y-m-d\TH:i:s\Z')];
}
