var body;

function onLoad() {
    var elements = document.getElementsByTagName('*');
        var styles = new Array()
        for(i = 0; i < elements.length; i++) {
            var oldStyle = elements[i].style.fontSize;
            var units = oldStyle.match(/[^0-9]+/);
            var oldVal = Number(oldStyle.substr(0, oldStyle.indexOf(units)).trim());
            styles.push(oldVal + units);
            elements[i].style.fontSize = oldStyle;
        }
        body = document.getElementsByTagName('body')[0].innerHTML;
        var insertion = styles + '<br><hr>';
        document.getElementsByTagName('body')[0].innerHTML = insertion + body;
}

function setFolding() {
    var elements = document.body.getElementsByClass('usecase');
        for(i = 0; i < elements.length; i++) {
            elements[i].style.fontSize = oldStyle;
        }
}

//function explainHeadWord(hwElement) {
//    jsBridge.jsHandleQuery(hwElement.innerHTML);
//}

function explainHeadWord(hwElement, highlight) {
    jsBridge.jsHandleQuery(hwElement.innerHTML, highlight);
}


function testBridge() {
    jsBridge.jsTest();
}

function decFontSize() {
//    var elements = document.body.children;
    var elements = document.getElementsByTagName('*');
//    var stylesOld = [0], stylesNew = [0];
    for(i = 0; i < elements.length; i++) {
        var oldStyle = elements[i].style.fontSize;
        var units = oldStyle.match(/[^0-9]+/);
        var oldVal = Number(oldStyle.substr(0, oldStyle.indexOf(units)).trim());
//        stylesOld.push(oldVal + units);
        elements[i].style.fontSize = --oldVal + units;
//        stylesNew.push(oldVal+units);
    }
//    var insertion = stylesOld + '<br>' + stylesNew  + '<br><hr>';
//        document.getElementsByTagName('body')[0].innerHTML = insertion + body;
}

function incFontSize() {
//    var elements = document.body.children;
    var elements = document.getElementsByTagName('*');
//    var stylesOld = [0], stylesNew = [0];
    for(i = 0; i < elements.length; i++) {
        var oldStyle = elements[i].style.fontSize;
        var units = oldStyle.match(/[^0-9]+/);
        var oldVal = Number(oldStyle.substr(0, oldStyle.indexOf(units)).trim());
//        stylesOld.push(oldVal + units);
        elements[i].style.fontSize = ++oldVal + units;
//        stylesNew.push(oldVal+units);
    }
//    var insertion = stylesOld + '<br>' + stylesNew  + '<br><hr>';
//        document.getElementsByTagName('body')[0].innerHTML = insertion + body;
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