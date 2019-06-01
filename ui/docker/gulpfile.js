var gulp = require('gulp');
var source = require('vinyl-source-stream');
var buffer = require('vinyl-buffer');
var watch = require('gulp-watch');
// var gutil = require('gulp-util');
// var browserify = require('browserify');
var babel = require('gulp-babel');

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
            .pipe(gulp.dest('./deploy'))
    })
})

gulp.task('default', gulp.parallel(['jsx', 'watch_jsx']))
