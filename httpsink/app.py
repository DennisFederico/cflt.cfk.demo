from flask import Flask, request, jsonify
import logging

app = Flask(__name__)

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

@app.route('/log', methods=['POST'])
def log_payload():
    try:
        payload = request.get_json()
        if payload is None:
            logging.warning("Received empty or non-JSON payload.")
            return jsonify({"message": "Invalid payload"}), 400

        logging.info(f"Received payload: {payload}")  # Log the entire payload

        return jsonify({"message": "Payload received and logged"}), 200

    except Exception as e:
        logging.error(f"Error processing payload: {e}")
        logging.error(f"Received: {request.data}")
        return jsonify({"message": "Error processing payload"}), 500

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)  # host='0.0.0.0' makes it accessible from outside the container