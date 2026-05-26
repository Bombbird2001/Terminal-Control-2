import argparse
import os
import uvicorn

if __name__ == '__main__':
	parser = argparse.ArgumentParser()
	parser.add_argument("--reload", action=argparse.BooleanOptionalAction, default=False)
	parser.add_argument("--port", type=int, required=True)
	args = parser.parse_args()
	reload = args.reload
	port = args.port
	uvicorn.run("tc2_multiplayer_notifier_bot:app", port=port, reload=reload, access_log=False)