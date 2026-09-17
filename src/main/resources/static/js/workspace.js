// ========================================
// LOAD WORKSPACE
// ========================================

window.addEventListener(
    "DOMContentLoaded",
    function () {
        checkActiveSession();
    }
);


// ========================================
// CHECK ACTIVE SESSION
// ========================================

async function checkActiveSession() {

    const status =
        document.getElementById(
            "workspaceSessionStatus"
        );

    const sessionIdElement =
        document.getElementById(
            "workspaceSessionId"
        );

    try {

        const response =
            await fetch(
                "/api/session/my-sessions",
                {
                    method: "GET",
                    credentials: "same-origin"
                }
            );

        if (!response.ok) {

            status.textContent =
                "Session check failed. HTTP "
                + response.status;

            console.error(
                "Session verification failed:",
                response.status,
                response.statusText
            );

            return;
        }

        const sessions =
            await response.json();

        // Handle authentication response
        if (!Array.isArray(sessions)) {

            status.textContent =
                sessions.message ||
                "Please login first.";

            return;
        }

        const activeSession =
            sessions.find(
                session =>
                    session.status === "ACTIVE"
            );

        if (!activeSession) {

            status.textContent =
                "No active secure session.";

            sessionIdElement.textContent = "";

            alert(
                "No active session. Please start a secure session first."
            );

            window.location.href =
                "dashboard.html";

            return;
        }

        status.textContent =
            "Status: ACTIVE";

        sessionIdElement.textContent =
            "Session ID: " +
            activeSession.sessionId;

        // Automatically load files
        loadFiles();

    } catch (error) {

        console.error(
            "SESSION CHECK ERROR:",
            error
        );

        status.textContent =
            "Server connection failed.";
    }
}


// ========================================
// UPLOAD FILE
// ========================================

async function uploadFile() {

    const fileInput =
        document.getElementById(
            "fileInput"
        );

    const message =
        document.getElementById(
            "uploadMessage"
        );

    if (!fileInput.files.length) {

        message.textContent =
            "Please select a file.";

        return;
    }

    const file =
        fileInput.files[0];

    if (!file.name || file.name.trim() === "") {

        message.textContent =
            "Invalid filename.";

        return;
    }

    const formData =
        new FormData();

    formData.append(
        "file",
        file
    );

    message.textContent =
        "Uploading...";

    try {

        const response =
            await fetch(
                "/api/workspace/upload",
                {
                    method: "POST",
                    credentials: "same-origin",
                    body: formData
                }
            );

        const result =
            await response.text();

        if (response.ok) {

            message.textContent =
                result;

            fileInput.value = "";

            loadFiles();

        } else {

            message.textContent =
                "Upload failed: " +
                result;
        }

    } catch (error) {

        console.error(
            "UPLOAD ERROR:",
            error
        );

        message.textContent =
            "Server connection failed.";
    }
}


// ========================================
// LOAD FILES
// ========================================

async function loadFiles() {

    const fileList =
        document.getElementById(
            "fileList"
        );

    if (!fileList) {
        return;
    }

    fileList.innerHTML =
        "<p>Loading files...</p>";

    try {

        const response =
            await fetch(
                "/api/workspace/files",
                {
                    method: "GET",
                    credentials: "same-origin"
                }
            );

        if (!response.ok) {

            fileList.innerHTML =
                "<p>Unable to load files.</p>";

            return;
        }

        const files =
            await response.json();

        if (!Array.isArray(files)
                || files.length === 0) {

            fileList.innerHTML =
                "<p>No files in your workspace.</p>";

            return;
        }

        fileList.innerHTML = "";

        files.forEach(
            function (filename) {

                const fileItem =
                    document.createElement(
                        "div"
                    );

                fileItem.className =
                    "session-item";

                const name =
                    document.createElement(
                        "strong"
                    );

                // textContent prevents HTML injection
                name.textContent =
                    filename;

                const downloadButton =
                    document.createElement(
                        "button"
                    );

                downloadButton.textContent =
                    "Download";

                downloadButton.onclick =
                    function () {

                        downloadFile(
                            filename
                        );
                    };

                const deleteButton =
                    document.createElement(
                        "button"
                    );

                deleteButton.textContent =
                    "Delete";

                deleteButton.onclick =
                    function () {

                        deleteFile(
                            filename
                        );
                    };

                fileItem.appendChild(
                    name
                );

                fileItem.appendChild(
                    document.createElement("br")
                );

                fileItem.appendChild(
                    downloadButton
                );

                fileItem.appendChild(
                    deleteButton
                );

                fileList.appendChild(
                    fileItem
                );
            }
        );

    } catch (error) {

        console.error(
            "LOAD FILES ERROR:",
            error
        );

        fileList.innerHTML =
            "<p>Server connection failed.</p>";
    }
}


// ========================================
// DOWNLOAD FILE
// ========================================

function downloadFile(filename) {

    if (!filename) {
        return;
    }

    const encodedFilename =
        encodeURIComponent(
            filename
        );

    window.location.href =
        "/api/workspace/download/" +
        encodedFilename;
}


// ========================================
// DELETE FILE
// ========================================

async function deleteFile(filename) {

    if (!filename) {
        return;
    }

    const confirmed =
        confirm(
            "Are you sure you want to delete '" +
            filename +
            "'?"
        );

    if (!confirmed) {
        return;
    }

    const message =
        document.getElementById(
            "workspaceMessage"
        );

    try {

        const response =
            await fetch(
                "/api/workspace/file/" +
                encodeURIComponent(filename),
                {
                    method: "DELETE",
                    credentials: "same-origin"
                }
            );

        const result =
            await response.text();

        if (response.ok) {

            message.textContent =
                result;

            loadFiles();

        } else {

            message.textContent =
                "Delete failed: " +
                result;
        }

    } catch (error) {

        console.error(
            "DELETE FILE ERROR:",
            error
        );

        message.textContent =
            "Server connection failed.";
    }
}


// ========================================
// OPEN SECURE WORKSPACE
// ========================================

async function openSecureWorkspace() {

    const message =
        document.getElementById(
            "workspaceMessage"
        );

    message.textContent =
        "Opening secure workspace...";

    try {

        const response =
            await fetch(
                "/api/workspace/open",
                {
                    method: "POST",
                    credentials: "same-origin"
                }
            );

        const result =
            await response.text();

        if (response.ok) {

            message.textContent =
                result;

        } else {

            message.textContent =
                "Unable to open workspace: " +
                result;
        }

    } catch (error) {

        console.error(
            "OPEN WORKSPACE ERROR:",
            error
        );

        message.textContent =
            "Server connection failed.";
    }
}


// ========================================
// END SESSION
// ========================================

async function endWorkspaceSession() {

    const sessionStatus =
        document.getElementById(
            "workspaceSessionStatus"
        );

    const sessionIdElement =
        document.getElementById(
            "workspaceSessionId"
        );

    const message =
        document.getElementById(
            "workspaceMessage"
        );

    try {

        // Get active session from backend
        const response =
            await fetch(
                "/api/session/my-sessions",
                {
                    method: "GET",
                    credentials: "same-origin"
                }
            );

        if (!response.ok) {

            message.textContent =
                "Unable to find active session.";

            return;
        }

        const sessions =
            await response.json();

        if (!Array.isArray(sessions)) {

            message.textContent =
                sessions.message ||
                "Please login first.";

            return;
        }

        const activeSession =
            sessions.find(
                session =>
                    session.status === "ACTIVE"
            );

        if (!activeSession) {

            message.textContent =
                "No active session found.";

            return;
        }

        const confirmed =
            confirm(
                "End the secure session? " +
                "Your temporary workspace will be deleted."
            );

        if (!confirmed) {
            return;
        }

        const endResponse =
            await fetch(
                "/api/session/end?sessionId=" +
                encodeURIComponent(
                    activeSession.sessionId
                ),
                {
                    method: "POST",
                    credentials: "same-origin"
                }
            );

        const result =
            await endResponse.json();

        if (
            endResponse.ok &&
            result.status === "SESSION_ENDED"
        ) {

            sessionStatus.textContent =
                "Status: Session ended";

            sessionIdElement.textContent =
                "";

            message.textContent =
                "Session ended and workspace cleaned successfully.";

            setTimeout(
                function () {

                    window.location.href =
                        "dashboard.html";

                },
                1000
            );

        } else {

            message.textContent =
                result.message ||
                "Unable to end session.";
        }

    } catch (error) {

        console.error(
            "END SESSION ERROR:",
            error
        );

        message.textContent =
            "Server connection failed.";
    }
}


// ========================================
// BACK TO DASHBOARD
// ========================================

function goBackToDashboard() {

    window.location.href =
        "dashboard.html";
}

// ========================================
// OPEN SECURE BROWSER
// ========================================

async function openSecureBrowser() {

    const message =
        document.getElementById("browserMessage");

    if (!message) {
        return;
    }

    message.textContent =
        "Opening secure browser...";

    try {

        const response =
            await fetch(
                "/api/workspace/open-browser",
                {
                    method: "POST",
                    credentials: "same-origin"
                }
            );

        const result =
            await response.text();

        if (response.ok) {

            message.textContent =
                result;

        } else {

            message.textContent =
                "Unable to open secure browser: " +
                result;
        }

    } catch (error) {

        console.error(
            "OPEN SECURE BROWSER ERROR:",
            error
        );

        message.textContent =
            "Server connection failed.";
    }
}