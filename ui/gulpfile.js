var gulp = require('gulp');
var source = require('vinyl-source-stream');
var buffer = require('vinyl-buffer');
var watch = require('gulp-watch');
// var gutil = require('gulp-util');
// var browserify = require('browserify');
var babel = require('gulp-babel');
var browser_sync = require('browser-sync').create()

gulp.task('jsx', function() {
    return gulp.src('./src/*.jsx')
        .pipe(babel({
            plugins: ["transform-react-jsx"]
        }))
        .on("end", (e) => console.log("Working"))
        .pipe(gulp.dest('./deploy'))
})

gulp.task('watch_jsx', function(){
    return watch('./src/*.jsx', () => {
        gulp.src('./src/*.jsx')
            .pipe(babel({
                plugins: ["transform-react-jsx"]
            }))
            .on("end", (e) => console.log("Working"))
            .pipe(gulp.dest('./deploy'))
    })
})

gulp.task('browser_sync', function(){
    browser_sync.init({
        server: {
            baseDir: "./deploy/"
        }
    });
    gulp.watch("./deploy/*.js").on('change', browser_sync.reload);
})

gulp.task('default', gulp.parallel(['browser_sync', 'jsx', 'watch_jsx']))
