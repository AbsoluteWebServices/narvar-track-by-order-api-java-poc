# Setup

Environment Variables needed to run the application:

```bash
RETAILER_MONIKER=example
HMAC_TOKEN=HMAC_TOKEN
```

Request your moniker from your customer success manager. 

To get the HMAC token, visit Admin > Company in the Narvar Hub.

# Test

To test the application, after setting the environment variables, open your browser and navigate to:

http://localhost:8080/track/order/1234567890

You should see a JSON response with the tracking information for the order number 1234567890.


