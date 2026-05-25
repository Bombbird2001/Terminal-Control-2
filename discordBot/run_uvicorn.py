import os
import uvicorn

if __name__ == '__main__':
	reload = os.environ.get('UVICORN_RELOAD', '').lower() in ('1', 'true', 'yes')
	uvicorn.run("tc2_multiplayer_notifier_bot:app", port=8000, reload=reload, access_log=False)