param(
    [string]$HostName = "localhost"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:Results = New-Object System.Collections.Generic.List[object]

function Write-Step {
    param(
        [string]$Service,
        [string]$Message
    )

    Write-Host "[$Service] $Message" -ForegroundColor Cyan
}

function Add-Result {
    param(
        [string]$Service,
        [string]$Check,
        [string]$Value
    )

    $script:Results.Add([pscustomobject]@{
            Service = $Service
            Check   = $Check
            Value   = $Value
        })
}

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Load-EnvFile {
    param([string]$Path)

    $map = @{}
    if (-not (Test-Path $Path)) {
        return $map
    }

    foreach ($line in Get-Content $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line.TrimStart().StartsWith("#")) {
            continue
        }
        if ($line -notmatch "=") {
            continue
        }

        $parts = $line -split "=", 2
        $map[$parts[0].Trim()] = $parts[1].Trim()
    }

    return $map
}

function ConvertTo-Base64Url {
    param([byte[]]$Bytes)

    return [Convert]::ToBase64String($Bytes).TrimEnd("=") -replace "\+", "-" -replace "/", "_"
}

function New-TestJwtToken {
    param(
        [long]$UserId,
        [string]$Role,
        [string]$Secret
    )

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $headerJson = '{"alg":"HS384","typ":"JWT"}'
    $payloadJson = (@{
            userId    = $UserId
            email     = ("{0}@codesync.local" -f $Role.ToLower())
            username  = $Role.ToLower()
            role      = $Role
            provider  = "LOCAL"
            tokenType = "ACCESS"
            sub       = "$UserId"
            iat       = $now
            exp       = $now + 3600
        } | ConvertTo-Json -Compress)

    $header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($headerJson))
    $payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes($payloadJson))
    $signingInput = "$header.$payload"
    $hmac = [System.Security.Cryptography.HMACSHA384]::new([Text.Encoding]::UTF8.GetBytes($Secret))
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($signingInput)))

    return "$signingInput.$signature"
}

function New-AuthHeaders {
    param([string]$Token)

    return @{
        Authorization = "Bearer $Token"
    }
}

function Invoke-JsonApi {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [object]$Body
    )

    $params = @{
        Method = $Method
        Uri    = $Uri
    }

    if ($Headers) {
        $params.Headers = $Headers
    }

    if ($PSBoundParameters.ContainsKey("Body")) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    Write-Host ("  -> {0} {1}" -f $Method, $Uri) -ForegroundColor DarkGray
    return Invoke-RestMethod @params
}

function Wait-ForExecutionTerminal {
    param(
        [string]$ExecutionBase,
        [guid]$JobId,
        [string]$Token,
        [int]$TimeoutSeconds = 90
    )

    $terminal = @("COMPLETED", "FAILED", "TIMED_OUT", "CANCELLED")
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $job = Invoke-JsonApi -Method GET -Uri "$ExecutionBase/executions/$JobId" -Headers (New-AuthHeaders $Token)
        if ($terminal -contains [string]$job.status) {
            return $job
        }
        Start-Sleep -Seconds 2
    }

    throw "Execution job $JobId did not reach a terminal status within $TimeoutSeconds seconds."
}

$envFile = Load-EnvFile (Join-Path $PSScriptRoot "..\.env")
$jwtSecret = if ($env:JWT_SECRET) { $env:JWT_SECRET } elseif ($envFile.ContainsKey("JWT_SECRET")) { $envFile["JWT_SECRET"] } else { "" }
Assert-True ($jwtSecret.Length -ge 32) "JWT_SECRET was not found in environment or .env."

$base = @{
    Gateway      = "http://$HostName`:8080"
    Registry     = "http://$HostName`:8761"
    Auth         = "http://$HostName`:8081"
    Project      = "http://$HostName`:8082"
    File         = "http://$HostName`:8083"
    Collaboration = "http://$HostName`:8084"
    Execution    = "http://$HostName`:8085"
    Version      = "http://$HostName`:8086"
    Comment      = "http://$HostName`:8087"
    Notification = "http://$HostName`:8088"
}

Write-Step "Infra" "Checking registry and gateway health"
$registryHome = Invoke-WebRequest -UseBasicParsing -Uri $base.Registry
Add-Result "service-registry" "GET /" "$($registryHome.StatusCode)"

$gatewayHealth = Invoke-RestMethod -Uri "$($base.Gateway)/actuator/health"
Add-Result "api-gateway" "GET /actuator/health" $gatewayHealth.status

$suffix = Get-Date -Format "yyyyMMddHHmmss"
$user1 = @{
    username = "codesync_dev_$suffix"
    email    = "codesync.dev.$suffix@example.com"
    password = "Password123!"
}
$user2 = @{
    username = "codesync_peer_$suffix"
    email    = "codesync.peer.$suffix@example.com"
    password = "Password123!"
}

Write-Step "auth-service" "Registering test users"
$register1 = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/register" -Body $user1
$register2 = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/register" -Body $user2
Add-Result "auth-service" "POST /auth/register (user1)" $register1.message
Add-Result "auth-service" "POST /auth/register (user2)" $register2.message

Write-Step "auth-service" "Logging in test users"
$login1 = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/login" -Body @{ email = $user1.email; password = $user1.password }
$login2 = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/login" -Body @{ email = $user2.email; password = $user2.password }
$user1Token = [string]$login1.token
$user2Token = [string]$login2.token
$user1Refresh = [string]$login1.refreshToken
$user1Id = [long]$login1.user.userId
$user2Id = [long]$login2.user.userId
Add-Result "auth-service" "POST /auth/login (user1)" "userId=$user1Id"
Add-Result "auth-service" "POST /auth/login (user2)" "userId=$user2Id"

Write-Step "auth-service" "Testing profile, search, refresh, logout, and password change"
$profile = Invoke-JsonApi -Method GET -Uri "$($base.Auth)/auth/profile" -Headers (New-AuthHeaders $user1Token)
Add-Result "auth-service" "GET /auth/profile" $profile.username

$updatedProfile = Invoke-JsonApi -Method PUT -Uri "$($base.Auth)/auth/profile" -Headers (New-AuthHeaders $user1Token) -Body @{
    fullName  = "CodeSync Developer"
    bio       = "Smoke test profile update"
    avatarUrl = "https://example.com/avatar.png"
}
Add-Result "auth-service" "PUT /auth/profile" $updatedProfile.fullName

$searchResults = Invoke-JsonApi -Method GET -Uri "$($base.Auth)/auth/search?keyword=$suffix"
Add-Result "auth-service" "GET /auth/search" "results=$(@($searchResults).Count)"

$passwordChange = Invoke-JsonApi -Method PUT -Uri "$($base.Auth)/auth/password" -Headers (New-AuthHeaders $user1Token) -Body @{
    oldPassword = $user1.password
    newPassword = "Password123!Updated"
}
Add-Result "auth-service" "PUT /auth/password" $passwordChange.message
$user1.password = "Password123!Updated"

$login1 = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/login" -Body @{ email = $user1.email; password = $user1.password }
$user1Token = [string]$login1.token
$user1Refresh = [string]$login1.refreshToken
Add-Result "auth-service" "POST /auth/login (after password change)" "ok"

$refresh = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/refresh" -Headers @{ Authorization = "Bearer $user1Refresh" }
Add-Result "auth-service" "POST /auth/refresh" $refresh.tokenType

$logout = Invoke-JsonApi -Method POST -Uri "$($base.Auth)/auth/logout" -Headers (New-AuthHeaders $user1Token)
Add-Result "auth-service" "POST /auth/logout" $logout.message

Write-Step "project-service" "Creating and reading project data"
$project = Invoke-JsonApi -Method POST -Uri "$($base.Project)/projects" -Headers (New-AuthHeaders $user1Token) -Body @{
    name        = "CodeSync Smoke Project $suffix"
    description = "Service-to-service smoke test project"
    language    = "Java"
    visibility  = "PUBLIC"
    templateId  = "blank-java"
}
$projectId = [long]$project.projectId
Add-Result "project-service" "POST /projects" "projectId=$projectId"

$projectById = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/$projectId" -Headers (New-AuthHeaders $user1Token)
Add-Result "project-service" "GET /projects/{id}" $projectById.name

$ownerProjects = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/owner/$user1Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "project-service" "GET /projects/owner/{ownerId}" "count=$(@($ownerProjects).Count)"

$myProjects = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/my" -Headers (New-AuthHeaders $user1Token)
Add-Result "project-service" "GET /projects/my" "count=$(@($myProjects).Count)"

$memberProjects = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/member/$user2Id" -Headers (New-AuthHeaders $user2Token)
Add-Result "project-service" "GET /projects/member/{userId}" "count=$(@($memberProjects).Count)"

$publicProjects = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/public"
Add-Result "project-service" "GET /projects/public" "count=$(@($publicProjects).Count)"

$searchedProjects = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/search?keyword=$suffix"
Add-Result "project-service" "GET /projects/search" "count=$(@($searchedProjects).Count)"

$languageProjects = Invoke-JsonApi -Method GET -Uri "$($base.Project)/projects/language/Java"
Add-Result "project-service" "GET /projects/language/{language}" "count=$(@($languageProjects).Count)"

$updatedProject = Invoke-JsonApi -Method PUT -Uri "$($base.Project)/projects/$projectId" -Headers (New-AuthHeaders $user1Token) -Body @{
    name        = "CodeSync Smoke Project $suffix Updated"
    description = "Updated description"
    language    = "Java"
    visibility  = "PUBLIC"
    templateId  = "blank-java"
}
Add-Result "project-service" "PUT /projects/{id}" $updatedProject.name

$starResult = Invoke-JsonApi -Method PUT -Uri "$($base.Project)/projects/$projectId/star" -Headers (New-AuthHeaders $user1Token)
Add-Result "project-service" "PUT /projects/{id}/star" $starResult.message

$forkedProject = Invoke-JsonApi -Method POST -Uri "$($base.Project)/projects/$projectId/fork" -Headers @{ Authorization = "Bearer $user2Token" }
$forkedProjectId = [long]$forkedProject.projectId
Add-Result "project-service" "POST /projects/{id}/fork" "projectId=$forkedProjectId"

Write-Step "file-service" "Creating folders and files"
$folderSrc = Invoke-JsonApi -Method POST -Uri "$($base.File)/files/folder" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId  = $projectId
    folderName = "src"
    parentPath = "/"
}
$folderMain = Invoke-JsonApi -Method POST -Uri "$($base.File)/files/folder" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId  = $projectId
    folderName = "main"
    parentPath = "src"
}
$folderArchive = Invoke-JsonApi -Method POST -Uri "$($base.File)/files/folder" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId  = $projectId
    folderName = "archive"
    parentPath = "/"
}
Add-Result "file-service" "POST /files/folder" "src=$($folderSrc.fileId), main=$($folderMain.fileId), archive=$($folderArchive.fileId)"

$mainFile = Invoke-JsonApi -Method POST -Uri "$($base.File)/files" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId  = $projectId
    name       = "Main.java"
    parentPath = "src/main"
    language   = "java"
}
$mainFileId = [long]$mainFile.fileId

$notesFile = Invoke-JsonApi -Method POST -Uri "$($base.File)/files" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId  = $projectId
    name       = "notes.txt"
    parentPath = "src"
    language   = "text"
}
$notesFileId = [long]$notesFile.fileId
Add-Result "file-service" "POST /files" "mainFileId=$mainFileId, notesFileId=$notesFileId"

$mainMetadata = Invoke-JsonApi -Method GET -Uri "$($base.File)/files/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "GET /files/{id}" $mainMetadata.path

$mainInitialContent = Invoke-RestMethod -Method GET -Uri "$($base.File)/files/$mainFileId/content" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "GET /files/{id}/content" ("length={0}" -f $mainInitialContent.Length)

$javaSource = @'
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello CodeSync");
    }
}
'@
$mainUpdated = Invoke-JsonApi -Method PUT -Uri "$($base.File)/files/$mainFileId/content" -Headers (New-AuthHeaders $user1Token) -Body @{
    content = $javaSource
}
Add-Result "file-service" "PUT /files/{id}/content" ("updatedAt={0}" -f $mainUpdated.updatedAt)

$renamedNotes = Invoke-JsonApi -Method PUT -Uri "$($base.File)/files/$notesFileId/rename" -Headers (New-AuthHeaders $user1Token) -Body @{
    newName = "notes-renamed.txt"
}
Add-Result "file-service" "PUT /files/{id}/rename" $renamedNotes.name

$movedNotes = Invoke-JsonApi -Method PUT -Uri "$($base.File)/files/$notesFileId/move" -Headers (New-AuthHeaders $user1Token) -Body @{
    newParentPath = "archive"
}
Add-Result "file-service" "PUT /files/{id}/move" $movedNotes.path

$projectFiles = Invoke-JsonApi -Method GET -Uri "$($base.File)/files/project/$projectId" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "GET /files/project/{projectId}" "count=$(@($projectFiles).Count)"

$searchedFiles = Invoke-JsonApi -Method GET -Uri "$($base.File)/files/project/$projectId/search?keyword=Hello" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "GET /files/project/{projectId}/search" "count=$(@($searchedFiles).Count)"

$fileTree = Invoke-JsonApi -Method GET -Uri "$($base.File)/files/project/$projectId/tree" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "GET /files/project/{projectId}/tree" "rootKeys=$((@($fileTree.PSObject.Properties.Name)) -join ',')"

$deleteNotes = Invoke-RestMethod -Method DELETE -Uri "$($base.File)/files/$notesFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "DELETE /files/{id}" $deleteNotes

$restoreNotes = Invoke-RestMethod -Method POST -Uri "$($base.File)/files/$notesFileId/restore" -Headers (New-AuthHeaders $user1Token)
Add-Result "file-service" "POST /files/{id}/restore" $restoreNotes

Write-Step "version-service" "Creating snapshots and history"
$snapshot1 = Invoke-JsonApi -Method POST -Uri "$($base.Version)/versions/create" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId = $projectId
    fileId    = $mainFileId
    message   = "Initial snapshot"
    content   = $javaSource
    branch    = "main"
}
$snapshot1Id = [long]$snapshot1.snapshotId
Add-Result "version-service" "POST /versions/create (snapshot1)" "snapshotId=$snapshot1Id"

$javaSourceV2 = @'
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello CodeSync v2");
    }
}
'@
Invoke-JsonApi -Method PUT -Uri "$($base.File)/files/$mainFileId/content" -Headers (New-AuthHeaders $user1Token) -Body @{
    content = $javaSourceV2
} | Out-Null

$snapshot2 = Invoke-JsonApi -Method POST -Uri "$($base.Version)/versions/create" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId = $projectId
    fileId    = $mainFileId
    message   = "Updated greeting"
    content   = $javaSourceV2
    branch    = "main"
}
$snapshot2Id = [long]$snapshot2.snapshotId
Add-Result "version-service" "POST /versions/create (snapshot2)" "snapshotId=$snapshot2Id"

$snapshotById = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/$snapshot1Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/{snapshotId}" $snapshotById.message

$snapshotsByFile = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/file/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/file/{fileId}" "count=$(@($snapshotsByFile).Count)"

$snapshotsByProject = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/project/$projectId" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/project/{projectId}" "count=$(@($snapshotsByProject).Count)"

$snapshotsByBranch = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/branch/main" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/branch/{branch}" "count=$(@($snapshotsByBranch).Count)"

$latestSnapshot = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/latest/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/latest/{fileId}" ("snapshotId={0}" -f $latestSnapshot.snapshotId)

$diff = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/diff/$snapshot1Id/$snapshot2Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/diff/{snapshotId1}/{snapshotId2}" (($diff.PSObject.Properties.Name) -join ",")

$branchSnapshot = Invoke-JsonApi -Method POST -Uri "$($base.Version)/versions/createBranch" -Headers (New-AuthHeaders $user1Token) -Body @{
    snapshotId = $snapshot2Id
    branch     = "feature-$suffix"
    message    = "Create feature branch"
}
Add-Result "version-service" "POST /versions/createBranch" ("snapshotId={0}" -f $branchSnapshot.snapshotId)

$taggedSnapshot = Invoke-JsonApi -Method POST -Uri "$($base.Version)/versions/tag" -Headers (New-AuthHeaders $user1Token) -Body @{
    snapshotId = $snapshot2Id
    tag        = "smoke-$suffix"
}
Add-Result "version-service" "POST /versions/tag" $taggedSnapshot.tag

$history = Invoke-JsonApi -Method GET -Uri "$($base.Version)/versions/history/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "GET /versions/history/{fileId}" "count=$(@($history).Count)"

$restoredSnapshot = Invoke-JsonApi -Method POST -Uri "$($base.Version)/versions/restore/$snapshot1Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "version-service" "POST /versions/restore/{snapshotId}" ("snapshotId={0}" -f $restoredSnapshot.snapshotId)

Write-Step "comment-service" "Creating, replying to, and resolving comments"
$comment = Invoke-JsonApi -Method POST -Uri "$($base.Comment)/comments/add" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId       = $projectId
    fileId          = $mainFileId
    content         = "Please review the greeting text."
    lineNumber      = 3
    columnNumber    = 8
    parentCommentId = $null
    snapshotId      = $snapshot2Id
}
$commentId = [long]$comment.commentId
Add-Result "comment-service" "POST /comments/add" "commentId=$commentId"

$commentById = Invoke-JsonApi -Method GET -Uri "$($base.Comment)/comments/$commentId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "GET /comments/{commentId}" $commentById.content

$commentsByFile = Invoke-JsonApi -Method GET -Uri "$($base.Comment)/comments/file/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "GET /comments/file/{fileId}" "count=$(@($commentsByFile).Count)"

$commentsByProject = Invoke-JsonApi -Method GET -Uri "$($base.Comment)/comments/project/$projectId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "GET /comments/project/{projectId}" "count=$(@($commentsByProject).Count)"

$reply = Invoke-JsonApi -Method POST -Uri "$($base.Comment)/comments/add" -Headers (New-AuthHeaders $user2Token) -Body @{
    projectId       = $projectId
    fileId          = $mainFileId
    content         = "Looks good to me."
    parentCommentId = $commentId
}
$replyId = [long]$reply.commentId
Add-Result "comment-service" "POST /comments/add (reply)" "commentId=$replyId"

$replies = Invoke-JsonApi -Method GET -Uri "$($base.Comment)/comments/replies/$commentId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "GET /comments/replies/{commentId}" "count=$(@($replies).Count)"

$updatedComment = Invoke-JsonApi -Method PUT -Uri "$($base.Comment)/comments/update/$commentId" -Headers (New-AuthHeaders $user1Token) -Body @{
    content = "Please review the updated greeting text."
}
Add-Result "comment-service" "PUT /comments/update/{commentId}" $updatedComment.content

$commentsByLine = Invoke-JsonApi -Method GET -Uri "$($base.Comment)/comments/line/$mainFileId/3" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "GET /comments/line/{fileId}/{lineNumber}" "count=$(@($commentsByLine).Count)"

$commentCount = Invoke-JsonApi -Method GET -Uri "$($base.Comment)/comments/count/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "GET /comments/count/{fileId}" $commentCount.count

$resolvedComment = Invoke-JsonApi -Method PUT -Uri "$($base.Comment)/comments/resolve/$commentId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "PUT /comments/resolve/{commentId}" $resolvedComment.resolved

$unresolvedComment = Invoke-JsonApi -Method PUT -Uri "$($base.Comment)/comments/unresolve/$commentId" -Headers (New-AuthHeaders $user1Token)
Add-Result "comment-service" "PUT /comments/unresolve/{commentId}" $unresolvedComment.resolved

Invoke-RestMethod -Method DELETE -Uri "$($base.Comment)/comments/$replyId" -Headers (New-AuthHeaders $user2Token) | Out-Null
Add-Result "comment-service" "DELETE /comments/{commentId}" "reply deleted"

Write-Step "collaboration-service" "Testing session lifecycle"
$session1 = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/create" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId           = $projectId
    fileId              = $mainFileId
    language            = "Java"
    maxParticipants     = 4
    isPasswordProtected = $false
    sessionPassword     = $null
}
$session1Id = [guid]$session1.sessionId
Add-Result "collaboration-service" "POST /sessions/create" "sessionId=$session1Id"

$session1ById = Invoke-JsonApi -Method GET -Uri "$($base.Collaboration)/sessions/$session1Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "collaboration-service" "GET /sessions/{sessionId}" $session1ById.status

$sessionsByProject = Invoke-JsonApi -Method GET -Uri "$($base.Collaboration)/sessions/project/$projectId" -Headers (New-AuthHeaders $user1Token)
Add-Result "collaboration-service" "GET /sessions/project/{projectId}" "count=$(@($sessionsByProject).Count)"

$join1 = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/join" -Headers (New-AuthHeaders $user2Token) -Body @{
    sessionId       = "$session1Id"
    role            = "EDITOR"
    sessionPassword = $null
}
$participantId = [long]$join1.participantId
Add-Result "collaboration-service" "POST /sessions/join" "participantId=$participantId"

$participants1 = Invoke-JsonApi -Method GET -Uri "$($base.Collaboration)/sessions/participants/$session1Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "collaboration-service" "GET /sessions/participants/{sessionId}" "count=$(@($participants1).Count)"

$activeSession = Invoke-JsonApi -Method GET -Uri "$($base.Collaboration)/sessions/active/$projectId/$mainFileId" -Headers (New-AuthHeaders $user1Token)
Add-Result "collaboration-service" "GET /sessions/active/{projectId}/{fileId}" "$($activeSession.sessionId)"

$cursor = Invoke-JsonApi -Method PUT -Uri "$($base.Collaboration)/sessions/cursor" -Headers (New-AuthHeaders $user1Token) -Body @{
    sessionId   = "$session1Id"
    cursorLine  = 3
    cursorCol   = 12
}
Add-Result "collaboration-service" "PUT /sessions/cursor" ("line={0},col={1}" -f $cursor.cursorLine, $cursor.cursorCol)

$leave = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/leave" -Headers (New-AuthHeaders $user2Token) -Body @{
    sessionId = "$session1Id"
}
Add-Result "collaboration-service" "POST /sessions/leave" $leave.message

$end1 = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/end" -Headers (New-AuthHeaders $user1Token) -Body @{
    sessionId = "$session1Id"
}
Add-Result "collaboration-service" "POST /sessions/end" $end1.message

$session2 = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/create" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId           = $projectId
    fileId              = $mainFileId
    language            = "Java"
    maxParticipants     = 4
    isPasswordProtected = $false
    sessionPassword     = $null
}
$session2Id = [guid]$session2.sessionId

$join2 = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/join" -Headers (New-AuthHeaders $user2Token) -Body @{
    sessionId       = "$session2Id"
    role            = "VIEWER"
    sessionPassword = $null
}
$participantId2 = [long]$join2.participantId

$kick = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/kick" -Headers (New-AuthHeaders $user1Token) -Body @{
    sessionId      = "$session2Id"
    participantId  = $participantId2
}
Add-Result "collaboration-service" "POST /sessions/kick" $kick.message

$end2 = Invoke-JsonApi -Method POST -Uri "$($base.Collaboration)/sessions/end" -Headers (New-AuthHeaders $user1Token) -Body @{
    sessionId = "$session2Id"
}
Add-Result "collaboration-service" "POST /sessions/end (session2)" $end2.message

Write-Step "execution-service" "Submitting and reading execution jobs"
$supportedLanguages = Invoke-JsonApi -Method GET -Uri "$($base.Execution)/executions/supportedLanguages" -Headers (New-AuthHeaders $user1Token)
Assert-True (@($supportedLanguages) -contains "java") "Execution service did not report java in supported languages."
Add-Result "execution-service" "GET /executions/supportedLanguages" ((@($supportedLanguages)) -join ",")

$javaVersion = Invoke-JsonApi -Method GET -Uri "$($base.Execution)/executions/languageVersion/java" -Headers (New-AuthHeaders $user1Token)
Add-Result "execution-service" "GET /executions/languageVersion/{language}" $javaVersion.version

$executionJob = Invoke-JsonApi -Method POST -Uri "$($base.Execution)/executions/submit" -Headers (New-AuthHeaders $user1Token) -Body @{
    projectId  = $projectId
    fileId     = $mainFileId
    language   = "java"
    sourceCode = $javaSourceV2
    stdin      = ""
}
$executionJobId = [guid]$executionJob.jobId
Add-Result "execution-service" "POST /executions/submit" "jobId=$executionJobId"

$finalJob = Wait-ForExecutionTerminal -ExecutionBase $base.Execution -JobId $executionJobId -Token $user1Token
Add-Result "execution-service" "GET /executions/{jobId}" $finalJob.status

$jobsByUser = Invoke-JsonApi -Method GET -Uri "$($base.Execution)/executions/user/$user1Id" -Headers (New-AuthHeaders $user1Token)
Add-Result "execution-service" "GET /executions/user/{userId}" "count=$(@($jobsByUser).Count)"

$jobsByProject = Invoke-JsonApi -Method GET -Uri "$($base.Execution)/executions/project/$projectId" -Headers (New-AuthHeaders $user1Token)
Add-Result "execution-service" "GET /executions/project/{projectId}" "count=$(@($jobsByProject).Count)"

$executionResult = Invoke-JsonApi -Method GET -Uri "$($base.Execution)/executions/result/$executionJobId" -Headers (New-AuthHeaders $user1Token)
Add-Result "execution-service" "GET /executions/result/{jobId}" ($executionResult.stdout.Trim())

$executionStats = Invoke-JsonApi -Method GET -Uri "$($base.Execution)/executions/stats" -Headers (New-AuthHeaders $user1Token)
Add-Result "execution-service" "GET /executions/stats" ("total={0}" -f $executionStats.totalExecutions)

try {
    $cancelSource = @'
public class Main {
    public static void main(String[] args) throws Exception {
        Thread.sleep(30000);
        System.out.println("finished");
    }
}
'@
    $cancelJob = Invoke-JsonApi -Method POST -Uri "$($base.Execution)/executions/submit" -Headers (New-AuthHeaders $user1Token) -Body @{
        projectId  = $projectId
        fileId     = $mainFileId
        language   = "java"
        sourceCode = $cancelSource
        stdin      = ""
    }
    Start-Sleep -Seconds 1
    $cancelled = Invoke-JsonApi -Method POST -Uri "$($base.Execution)/executions/cancel/$($cancelJob.jobId)" -Headers (New-AuthHeaders $user1Token)
    Add-Result "execution-service" "POST /executions/cancel/{jobId}" $cancelled.status
}
catch {
    Add-Result "execution-service" "POST /executions/cancel/{jobId}" "warning: $($_.Exception.Message)"
}

Write-Step "notification-service" "Sending single and bulk notifications"
$notification = Invoke-JsonApi -Method POST -Uri "$($base.Notification)/notifications/send" -Headers (New-AuthHeaders $user1Token) -Body @{
    recipientId   = $user2Id
    type          = "COMMENT"
    title         = "Review request"
    message       = "Please review the latest Main.java update."
    relatedId     = $commentId
    relatedType   = "COMMENT"
    recipientEmail = $null
    sendEmail     = $false
}
$notificationId = [long]$notification.notificationId
Add-Result "notification-service" "POST /notifications/send" "notificationId=$notificationId"

$notificationsForUser2 = Invoke-JsonApi -Method GET -Uri "$($base.Notification)/notifications/recipient/$user2Id" -Headers (New-AuthHeaders $user2Token)
Add-Result "notification-service" "GET /notifications/recipient/{recipientId}" "count=$(@($notificationsForUser2).Count)"

$unreadForUser2 = Invoke-JsonApi -Method GET -Uri "$($base.Notification)/notifications/unreadCount/$user2Id" -Headers (New-AuthHeaders $user2Token)
Add-Result "notification-service" "GET /notifications/unreadCount/{recipientId}" $unreadForUser2.unreadCount

$readNotification = Invoke-JsonApi -Method PUT -Uri "$($base.Notification)/notifications/read/$notificationId" -Headers (New-AuthHeaders $user2Token)
Add-Result "notification-service" "PUT /notifications/read/{notificationId}" $readNotification.isRead

$adminToken = New-TestJwtToken -UserId 1 -Role "ADMIN" -Secret $jwtSecret
$bulkNotifications = Invoke-JsonApi -Method POST -Uri "$($base.Notification)/notifications/sendBulk" -Headers (New-AuthHeaders $adminToken) -Body @{
    recipientIds   = @($user1Id, $user2Id)
    type           = "SESSION_INVITE"
    title          = "Smoke test broadcast"
    message        = "Admin bulk notification test"
    relatedId      = $projectId
    relatedType    = "PROJECT"
    recipientEmails = @()
    sendEmail      = $false
}
Add-Result "notification-service" "POST /notifications/sendBulk" "count=$(@($bulkNotifications).Count)"

$allNotifications = Invoke-JsonApi -Method GET -Uri "$($base.Notification)/notifications/all" -Headers (New-AuthHeaders $adminToken)
Add-Result "notification-service" "GET /notifications/all" "count=$(@($allNotifications).Count)"

$markAllRead = Invoke-JsonApi -Method PUT -Uri "$($base.Notification)/notifications/readAll/$user2Id" -Headers (New-AuthHeaders $user2Token)
Add-Result "notification-service" "PUT /notifications/readAll/{recipientId}" $markAllRead.affectedCount

$deleteRead = Invoke-JsonApi -Method DELETE -Uri "$($base.Notification)/notifications/deleteRead/$user2Id" -Headers (New-AuthHeaders $user2Token)
Add-Result "notification-service" "DELETE /notifications/deleteRead/{recipientId}" $deleteRead.affectedCount

$notification2 = Invoke-JsonApi -Method POST -Uri "$($base.Notification)/notifications/send" -Headers (New-AuthHeaders $user1Token) -Body @{
    recipientId = $user2Id
    type        = "SNAPSHOT"
    title       = "Snapshot restored"
    message     = "The file was restored to an earlier snapshot."
    relatedId   = $snapshot1Id
    relatedType = "SNAPSHOT"
    sendEmail   = $false
}
Invoke-RestMethod -Method DELETE -Uri "$($base.Notification)/notifications/$($notification2.notificationId)" -Headers (New-AuthHeaders $user2Token) | Out-Null
Add-Result "notification-service" "DELETE /notifications/{notificationId}" "deleted"

Write-Step "project-service" "Finishing archive and delete checks"
$archived = Invoke-JsonApi -Method PUT -Uri "$($base.Project)/projects/$projectId/archive" -Headers (New-AuthHeaders $user1Token)
Add-Result "project-service" "PUT /projects/{id}/archive" $archived.message

$deletedFork = Invoke-JsonApi -Method DELETE -Uri "$($base.Project)/projects/$forkedProjectId" -Headers (New-AuthHeaders $user2Token)
Add-Result "project-service" "DELETE /projects/{id}" $deletedFork.message

Write-Step "auth-service" "Deactivating secondary user"
$deactivate = Invoke-JsonApi -Method PUT -Uri "$($base.Auth)/auth/deactivate" -Headers (New-AuthHeaders $user2Token)
Add-Result "auth-service" "PUT /auth/deactivate" $deactivate.message

Write-Host ""
Write-Host "CodeSync API smoke test summary" -ForegroundColor Green
$script:Results | Sort-Object Service, Check | Format-Table -AutoSize

Write-Host ""
Write-Host "Useful docs" -ForegroundColor Green
Write-Host "  Gateway Swagger:       $($base.Gateway)/swagger-ui.html"
Write-Host "  Registry:              $($base.Registry)"
Write-Host "  Auth Swagger:          $($base.Auth)/swagger-ui.html"
Write-Host "  Project Swagger:       $($base.Project)/swagger-ui.html"
Write-Host "  File Swagger:          $($base.File)/swagger-ui.html"
Write-Host "  Collaboration Swagger: $($base.Collaboration)/swagger-ui.html"
Write-Host "  Execution Swagger:     $($base.Execution)/swagger-ui.html"
Write-Host "  Version Swagger:       $($base.Version)/swagger-ui.html"
Write-Host "  Comment Swagger:       $($base.Comment)/swagger-ui.html"
Write-Host "  Notification Swagger:  $($base.Notification)/swagger-ui.html"
