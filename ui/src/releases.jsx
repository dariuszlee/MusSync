'use strict';

class LikeButton extends React.Component {
  constructor(props) {
    super(props);
    this.state = { liked: false };
  }

  render() {
      return <button onClick={() => this.setState({ liked: true })}>
               Like
             </button>
  }
}

const domContainer = document.querySelector('#release_curr');
ReactDOM.render(React.createElement(LikeButton), domContainer);
