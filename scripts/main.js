function setup_venus() {
    console.log("----------THIS IS THE END OF THE EXPECTED GET ERRORS!----------");
    try {
        load_update_message("Initializing codeMirror");
        window.venus_main = window.venus;
        window.driver = venus_main.venus.Driver;
        window.venus.api = venus_main.venus.api.API;
        window.simulatorAPI = venus_main.venus.api.venusbackend.simulator.Simulator;
        window.editor = document.getElementById("asm-editor");
        window.codeMirror = CodeMirror.fromTextArea(editor,
            {
                lineNumbers: true,
                mode: "riscv",
                indentUnit: 4,
                autofocus: true,
                lint: true,
                autoRefresh:true,
            }
        );
        if (window.CodeMirror.mac) { // This uses a custom codemirror which exposes this check.
            codeMirror.addKeyMap({"Cmd-/": function(cm){cm.execCommand('toggleComment')}})
        } else {
            codeMirror.addKeyMap({"Ctrl-/": function(cm){cm.execCommand('toggleComment')}})
        }
        window.codeMirror.setSize("100%", "88vh");
    } catch (e) {
        console.error(e);
        load_error(e.toString())
    }
}

function local_riscv() {
    load_update_message("Loading stylesheets!");
    load_update_message("Loading required file (local): js/codemirror/risc-mode.js");
    loadScript("js/codemirror/risc-mode.js", "var msg='COULD NOT LOAD RISCVMODE SCRIPT!';load_error(msg);", "local_kotlin();");
}

function local_kotlin() {
    load_update_message("Loading required file (local): kotlin.js");
    // loadScript("https://try.kotlinlang.org/static/kotlin/1.3.11/kotlin.js", "alert('COULD NOT LOAD KOTLIN SCRIPT!');", "local_venus();");
    loadScript("../../../build/kotlin-js-min/main/kotlin.js", "var msg='COULD NOT LOAD KOTLIN SCRIPT!';load_error(msg);", "local_venus();");
}

function local_venus() {
    load_update_message("Loading required file (local): venus.js");
    temp_fix();
    loadScript("../../../build/kotlin-js-min/main/venus.js", "var msg='COULD NOT LOAD VENUS SCRIPT!';load_error(msg);", "setup_venus();")
}

function deploy_venus() {
    load_update_message("Loading required file: venus.js");
    temp_fix();
    loadScript("js/venus.js",  "var msg='COULD NOT LOAD VENUS SCRIPT!';load_error(msg);", "setup_venus();");
}

function main_venus() {
    load_update_message("Loading required file: kotlin.js");
    loadScript("js/kotlin.js", "load_update_message(\"Loading required file: kotlin.js...FAILED!\");local_riscv();", "deploy_venus();");
}

function temp_fix() {
    if (typeof kotlin !== "undefined" && typeof kotlin.kotlin !== "undefined" && typeof kotlin.kotlin.Number === "undefined") {
        kotlin.kotlin.Number = function (){};
        kotlin.kotlin.Number.prototype.call = function(a){};
    }
}

function load_error(msg) {
    var e = document.getElementById("loading_text");
    e.innerHTML = "An error has occurred while loading Venus!";
    if (typeof msg == "string") {
        e.innerHTML += "<br><br><div style=\"font-size:0.75em\">" + msg.replace(/\n/g, '<br>') + "</div>";
    }
    e.innerHTML += "<br><br><br><div style=\"font-size:0.6em\">Try reloading the page. If the issue persists, please file a ticket on <a href='https://github.com/61c-teach/venus/issues'>GitHub</a>.</div>";
    document.getElementById("loader").style.opacity = 0;
}

window.driver_load_done = function () {
    /* Check if packages are all loaded */
    h  = function(){
        if (driver.driver_complete_loading) {
            load_done();
            return
        }
        setTimeout(h, 10);
    };
    setTimeout(h, 10);
};

window.load_done = function () {
    load_update_message("Done!");
    window.document.body.classList.add("loaded");
    window.onerror = null;
};

function load_update_message(msg) {
    elm = document.getElementById("load-msg");
    if (elm === null) {
        msg = "Could not update the load message to: " + msg;
        load_error(msg);
        console.error(msg);
        return
    }
    elm.innerHTML = msg.replace(/\n/g, "<br>");
}

async function isUrlFound(url) {
    try {
        const response = await fetch(url, {
            method: 'HEAD',
            cache: 'no-cache'
        });

        return response.status === 200;

    } catch(error) {
        // console.log(error);
        return false;
    }
}

function addCss(fileName) {

    var head = document.head;
    var link = document.createElement("link");

    link.type = "text/css";
    link.rel = "stylesheet";
    link.href = fileName;

    head.appendChild(link);
}

function load_error_fn(message, source, lineno, colno, error) {
    load_error(message + "\nMore info in the console.");
}

window.onerror = load_error_fn;
window.default_alert = window.alert;
window.alert = function(message) {
    alertify.alert(message.replace(/\n/g, "<br>"));
    // alertify.alert.apply(this, arguments);
};
// window.confirm = alertify.confirm;
// window.prompt = alertify.prompt;
alertify.alert()
    .setting({
        'title': 'Venus'
    });
alertify.confirm()
    .setting({
        'title': 'Venus'
    });
alertify.prompt()
    .setting({
        'title': 'Venus'
    });

main_venus();



// function dark() {
//     document.getElementById("venus_theme").href = "css/themes/venus_dark.css";
//     codeMirror.setOption("theme", "ayu-dark");
// }
//
// function light() {
//     document.getElementById("venus_theme").href = "css/themes/venus.css";
//     codeMirror.setOption("theme", "default");
// }
//
// function get_theme() {
//     return localStorage.getItem("venus_theme");
// }
//
// function set_theme(theme) {
//     return localStorage.setItem("venus_theme", theme);
// }
//
// function toggle_theme() {
//     is_dark = get_theme() === "dark";
//     if (is_dark) {
//         set_theme("light");
//         light();
//     } else {
//         set_theme("dark");
//         dark();
//     }
// }
//
// function setup_theme() {
//     var thm = get_theme();
//     if (thm === undefined || thm == null) {
//         console.log("No theme selected! Setting to...");
//         try {
//             if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
//                 console.log("dark!");
//                 set_theme("dark");
//             } else {
//                 console.log("light!");
//                 set_theme("light");
//             }
//         } catch (e) {
//             console.log("Error!");
//             console.log(e);
//             console.log("Light!");
//             set_theme("light");
//         }
//     }
//     if (get_theme() === "dark") {
//         dark();
//         document.getElementById('themeSwitch').click();
//     }
//     if (get_theme() === "light") {
//         // Pass
//     }
//
//     document.getElementById('themeSwitch').addEventListener('change', function(event){
//         toggle_theme();
//     });
// }

