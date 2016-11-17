
function onLoad() {
    var elements = document.body.getElementsByTagName('*');
        for(i = 0; i < elements.length; i++) {
            var oldStyle = elements[i].style.fontSize;
            var mes = oldStyle.match(/[^0-9]+/);
            var oldVal = Number(oldStyle.substr(0, oldStyle.indexOf(mes)).trim());
            elements[i].style.fontSize = oldStyle;
        }
}


function setFolding() {
    var elements = document.body.getElementsByClass('usecase');
            for(i = 0; i < elements.length; i++) {
                elements[i].style.fontSize = oldStyle;
            }
}

function decFontSize() {
//    var elements = document.body.children;
    var elements = document.body.getElementsByTagName('*');
    for(i = 0; i < elements.length; i++) {
        var oldStyle = elements[i].style.fontSize;
        var units = oldStyle.match(/[^0-9]+/);
        var oldVal = Number(oldStyle.substr(0, oldStyle.indexOf(units)).trim());
        elements[i].style.fontSize = --oldVal + units;
    }
}

function incFontSize() {
//    var elements = document.body.children;
    var elements = document.body.getElementsByTagName('*');
    for(i = 0; i < elements.length; i++) {
        var oldStyle = elements[i].style.fontSize;
        var units = oldStyle.match(/[^0-9]+/);
        var oldVal = Number(oldStyle.substr(0, oldStyle.indexOf(units)).trim());
        elements[i].style.fontSize = ++oldVal + units;
    }
}

function preventDefault(e) {
  e = e || window.event;
  if (e.preventDefault)
      e.preventDefault();
  e.returnValue = false;
}

function preventDefaultForScrollKeys(e) {
    if (keys[e.keyCode]) {
        preventDefault(e);
        return false;
    }
}

function disableScroll() {
  if (window.addEventListener) // older FF
      window.addEventListener('DOMMouseScroll', preventDefault, false);
  window.onwheel = preventDefault; // modern standard
  window.onmousewheel = document.onmousewheel = preventDefault; // older browsers, IE
}

function enableScroll() {
    if (window.removeEventListener)
        window.removeEventListener('DOMMouseScroll', preventDefault, false);
    window.onmousewheel = document.onmousewheel = null;
    window.onwheel = null;
}