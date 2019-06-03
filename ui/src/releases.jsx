'use strict';

class ReleaseContainer extends React.Component {
    constructor(props) {
        super(props);
        var states = ["loading", "prepared", "ready"]
        this.state = { load_state: "loading" };
    }

    render() {
        if(this.state.load_state == "loading"){
            return <div>Loading changed </div>
        }
        else if(this.state.load_state == "prepared"){
            return <div>Prepared: Loaded..</div>
        }
        else if(this.state.load_state == "ready"){
            return <div>Ready: Waiting..</div>
        }
        else{
            return <div>Error state...</div>
        }
    } 
}

const domContainer = document.querySelector('#release_curr');
ReactDOM.render(React.createElement(ReleaseContainer), domContainer);
