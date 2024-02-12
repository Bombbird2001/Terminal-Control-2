import uvicorn

if __name__ == '__main__':
	uvicorn.run("tc2_multiplayer_notifier_bot:app", port=8000, reload=True, access_log=False)