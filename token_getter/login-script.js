var utils = require('utils')
var casper = require('casper').create();

casper.on("page.error", function(msg, trace) {
    this.echo("Error: " + msg);
});

casper.on('remote.message', function(msg) {
    this.echo('remote message caught: ' + msg);
})

var url = "https://accounts.spotify.com/authorize/?client_id=40b76927fb1a4841b2114bcda79e829a&response_type=code&redirect_uri=https://example.com/callback&scope=user-read-private user-read-email&state=34fFs29kd09&show_dialog=true"

casper.start(url, function() {
    this.waitForSelector('div')
    // this.echo(this.getPageContent())
});

casper.then(function() {
    this.clickLabel('Log in to Spotify', 'a')
})

casper.thenEvaluate(function(){
})

casper.then(function(){
    this.capture("./Token.png")
})

casper.run();
